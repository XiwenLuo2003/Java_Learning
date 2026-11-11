package com.example.redis.seckill;

import com.example.redis.common.RedisIdWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀服务，结构对应黑马点评 VoucherOrderServiceImpl。
 * <p>
 * 正确链路：Lua（扣库存 + 一人一单 + XADD）→ Stream 消费组 → createOrder（数据库乐观锁 + 唯一索引兜底）。
 * 默认加载 seckill.lua；实验“加载旧脚本”时临时换成 seckill_old.lua，复现黑马点评当前仓库的 bug。
 */
@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);
    public static final long VOUCHER_ID = 1L;
    public static final String STREAM = "stream.orders";
    public static final String GROUP = "g1";
    public static final String CONSUMER = "c1";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = script("lua/seckill.lua");
    private static final DefaultRedisScript<Long> SECKILL_OLD_SCRIPT = script("lua/seckill_old.lua");

    private final StringRedisTemplate redis;
    private final RedisIdWorker idWorker;
    private final RedissonClient redisson;
    private final SeckillRepository repository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seckill-consumer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger consumed = new AtomicInteger();
    private final AtomicInteger pendingRetries = new AtomicInteger();

    /** 临时切到旧脚本，用来复现“库存扣了但没订单” */
    private volatile boolean useOldScript;

    public SeckillService(StringRedisTemplate redis, RedisIdWorker idWorker,
                          RedissonClient redisson, SeckillRepository repository) {
        this.redis = redis;
        this.idWorker = idWorker;
        this.redisson = redisson;
        this.repository = repository;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setLocation(new ClassPathResource(path));
        s.setResultType(Long.class);
        return s;
    }

    @PostConstruct
    public void startConsumer() {
        ensureGroup();
        executor.submit(this::consumeLoop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    /** XGROUP CREATE stream.orders g1 0 MKSTREAM：消费组不存在时创建，并顺便建流 */
    public void ensureGroup() {
        try {
            redis.execute(connection -> {
                connection.streamCommands().xGroupCreate(
                        STREAM.getBytes(), GROUP, ReadOffset.from("0"), true);
                return null;
            });
            log.info("创建消费组 {} / {}", STREAM, GROUP);
        } catch (Exception e) {
            // BUSYGROUP = 已经存在，正常
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                log.debug("消费组可能已存在: {}", e.getMessage());
            }
        }
    }

    /**
     * 下单入口。返回码：0 成功（只代表 Redis 预扣成功，订单由消费者异步落库），1 库存不足，2 重复下单。
     */
    public long seckill(long userId) {
        long orderId = idWorker.nextId("order");
        DefaultRedisScript<Long> script = useOldScript ? SECKILL_OLD_SCRIPT : SECKILL_SCRIPT;
        Long code = redis.execute(script, Collections.emptyList(),
                String.valueOf(VOUCHER_ID), String.valueOf(userId), String.valueOf(orderId));
        return code == null ? -1 : code;
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> list = redis.opsForStream().read(
                        Consumer.from(GROUP, CONSUMER),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM, ReadOffset.lastConsumed())
                );
                if (list == null || list.isEmpty()) {
                    continue;
                }
                MapRecord<String, Object, Object> record = list.get(0);
                handle(record);
                redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
                consumed.incrementAndGet();
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.warn("消费异常，转 pending-list 重试: {}", e.getMessage());
                handlePendingList();
            }
        }
    }

    /** pending-list：已经读走但还没 ACK 的消息。offset 用 "0" 而不是 ">" */
    private void handlePendingList() {
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> list = redis.opsForStream().read(
                        Consumer.from(GROUP, CONSUMER),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM, ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    return;
                }
                MapRecord<String, Object, Object> record = list.get(0);
                handle(record);
                redis.opsForStream().acknowledge(STREAM, GROUP, record.getId());
                pendingRetries.incrementAndGet();
                consumed.incrementAndGet();
            } catch (Exception e) {
                log.warn("pending-list 重试失败: {}", e.getMessage());
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handle(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        VoucherOrder order = new VoucherOrder();
        order.setId(Long.valueOf(values.get("id").toString()));
        order.setUserId(Long.valueOf(values.get("userId").toString()));
        order.setVoucherId(Long.valueOf(values.get("voucherId").toString()));
        createOrder(order);
    }

    /**
     * 落库：Redisson 锁（按用户）+ 一人一单查询 + 乐观扣库存 + 插入订单。
     * 唯一索引 uk_user_voucher 是最后一道防线。
     */
    public void createOrder(VoucherOrder order) {
        RLock lock = redisson.getLock("lock:order:" + order.getUserId());
        if (!lock.tryLock()) {
            log.warn("获取锁失败，用户 {} 可能重复下单", order.getUserId());
            return;
        }
        try {
            if (repository.orderCount(order.getUserId(), order.getVoucherId()) > 0) {
                log.warn("用户 {} 已购买过优惠券 {}", order.getUserId(), order.getVoucherId());
                return;
            }
            if (!repository.deductStock(order.getVoucherId())) {
                log.warn("库存不足 voucherId={}", order.getVoucherId());
                return;
            }
            repository.saveOrder(order);
        } finally {
            lock.unlock();
        }
    }

    public void prepare(int stock) {
        repository.resetStock(VOUCHER_ID, stock);
        redis.opsForValue().set("seckill:stock:" + VOUCHER_ID, String.valueOf(stock));
        redis.delete("seckill:order:" + VOUCHER_ID);
        // 清掉上一次实验残留的 Stream（消费组会一起没）
        redis.delete(STREAM);
        ensureGroup();
        consumed.set(0);
        pendingRetries.set(0);
        useOldScript = false;
    }

    public void setUseOldScript(boolean useOldScript) {
        this.useOldScript = useOldScript;
    }

    public boolean isUseOldScript() {
        return useOldScript;
    }

    public int consumed() {
        return consumed.get();
    }

    public int pendingRetries() {
        return pendingRetries.get();
    }

    public SeckillRepository repository() {
        return repository;
    }

    public StringRedisTemplate redis() {
        return redis;
    }
}
