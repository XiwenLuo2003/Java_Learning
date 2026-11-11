package com.example.redis.seckill;

import com.example.redis.common.Concurrent;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 第 6 课：秒杀防超卖与 Redis Stream 异步下单。请求见 requests.http 第 6 组。
 */
@RestController
@RequestMapping("/seckill")
public class SeckillDemoController {

    private final SeckillService seckillService;
    private final StringRedisTemplate redis;

    public SeckillDemoController(SeckillService seckillService, StringRedisTemplate redis) {
        this.seckillService = seckillService;
        this.redis = redis;
    }

    /**
     * 实验 1：并发秒杀。默认库存 10，200 个用户（每人一次），看 Redis 预扣、数据库订单、Stream 消费是否对齐。
     * useOldScript=true 时加载 seckill_old.lua：Redis 库存扣了，但 Stream 没消息，数据库订单 = 0（黑马点评当前仓库的 bug）。
     */
    @GetMapping("/race")
    public Map<String, Object> race(@RequestParam(defaultValue = "10") int stock,
                                    @RequestParam(defaultValue = "200") int users,
                                    @RequestParam(defaultValue = "false") boolean useOldScript) {
        seckillService.prepare(stock);
        seckillService.setUseOldScript(useOldScript);

        List<Long> codes = Concurrent.run(users, i -> seckillService.seckill(1000L + i));
        long ok = codes.stream().filter(c -> c == 0).count();
        long soldOut = codes.stream().filter(c -> c == 1).count();
        long duplicated = codes.stream().filter(c -> c == 2).count();

        // 等消费者把 Stream 里的消息落库
        waitUntilIdle(3000);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("useOldScript", useOldScript);
        result.put("stock", stock);
        result.put("users", users);
        result.put("luaOk", ok);
        result.put("luaSoldOut", soldOut);
        result.put("luaDuplicated", duplicated);
        result.put("redisStockLeft", redis.opsForValue().get("seckill:stock:" + SeckillService.VOUCHER_ID));
        result.put("redisOrderSetSize", redis.opsForSet().size("seckill:order:" + SeckillService.VOUCHER_ID));
        result.put("dbStock", seckillService.repository().stock(SeckillService.VOUCHER_ID));
        result.put("dbOrders", seckillService.repository().orderTotal(SeckillService.VOUCHER_ID));
        result.put("consumed", seckillService.consumed());
        result.put("streamLength", redis.opsForStream().size(SeckillService.STREAM));
        result.put("note", useOldScript
                ? "旧脚本没 XADD：luaOk 等于库存，但 dbOrders=0、consumed=0。把脚本换成 seckill.lua 就通了"
                : "正确链路：luaOk == dbOrders == stock，redisStockLeft=0");
        return result;
    }

    /**
     * 实验 2：一人一单。同一个用户连点 20 次，只有第一次成功。
     */
    @GetMapping("/one-person-one-order")
    public Map<String, Object> onePersonOneOrder() {
        seckillService.prepare(10);
        long userId = 42L;
        List<Long> codes = Concurrent.run(20, i -> seckillService.seckill(userId));
        waitUntilIdle(2000);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("luaOk", codes.stream().filter(c -> c == 0).count());
        result.put("luaDuplicated", codes.stream().filter(c -> c == 2).count());
        result.put("dbOrders", seckillService.repository().orderTotal(SeckillService.VOUCHER_ID));
        result.put("dbOrderOfUser", seckillService.repository().orderCount(userId, SeckillService.VOUCHER_ID));
        return result;
    }

    /**
     * 实验 3：看 pending-list。先准备库存并发几单，再查消费组的 pending 摘要。
     * 正常消费完 pending = 0；如果想亲眼看 pending，可以临时把 createOrder 里抛异常。
     */
    @GetMapping("/pending")
    public Map<String, Object> pending(@RequestParam(defaultValue = "5") int stock) {
        seckillService.prepare(stock);
        Concurrent.run(stock, i -> seckillService.seckill(2000L + i));
        waitUntilIdle(3000);

        PendingMessagesSummary summary = redis.opsForStream()
                .pending(SeckillService.STREAM, SeckillService.GROUP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consumed", seckillService.consumed());
        result.put("pendingRetries", seckillService.pendingRetries());
        result.put("pendingTotal", summary == null ? 0 : summary.getTotalPendingMessages());
        result.put("dbOrders", seckillService.repository().orderTotal(SeckillService.VOUCHER_ID));
        result.put("note", "pendingTotal 应为 0。消息被读走但业务抛异常时会进 pending-list，"
                + "handlePendingList 用 XREADGROUP ... 0 捞出来重试，成功后再 XACK");
        return result;
    }

    private void waitUntilIdle(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            Long len = redis.opsForStream().size(SeckillService.STREAM);
            PendingMessagesSummary summary = null;
            try {
                summary = redis.opsForStream().pending(SeckillService.STREAM, SeckillService.GROUP);
            } catch (Exception ignored) {
                // 消费组刚重建时可能短暂查不到
            }
            long pending = summary == null ? 0 : summary.getTotalPendingMessages();
            // Stream 里的消息都被 ACK 后，length 仍可能 > 0（已消费的消息还在，直到 XTRIM）
            // 用“消费数追上 Lua 成功数”更准：看 redis order set 大小
            Long orderSet = redis.opsForSet().size("seckill:order:" + SeckillService.VOUCHER_ID);
            if (orderSet != null && seckillService.consumed() >= orderSet && pending == 0) {
                return;
            }
            // 旧脚本根本不发消息，立刻返回
            if (seckillService.isUseOldScript()) {
                Concurrent.sleep(200);
                return;
            }
            Concurrent.sleep(50);
            if (len != null && len == 0 && pending == 0) {
                return;
            }
        }
    }
}
