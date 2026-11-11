package com.example.mq.reliable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 下单和消费在这里。Outbox 和订单同一事务；消费日志和发券同一事务。
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbc;
    private final AtomicLong orderIds = new AtomicLong(1);

    public OrderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 订单和 outbox 要么一起成功，要么一起回滚。此时消息还没进 RabbitMQ */
    @Transactional
    public Map<String, Object> create(long userId) {
        long orderId = orderIds.getAndIncrement();
        jdbc.update("INSERT INTO tb_order (id, user_id, status, coupon_granted) VALUES (?, ?, 0, 0)", orderId, userId);
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO tb_outbox (order_id, status) VALUES (?, 0)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, orderId);
            return ps;
        }, keys);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("outboxId", keys.getKey().longValue());
        result.put("outboxStatus", 0);
        result.put("note", "事务已提交，消息还在本地表里。发布器每秒扫一次 status=0 的行");
        return result;
    }

    public List<Map<String, Object>> outbox() {
        return jdbc.queryForList("SELECT id, order_id, status, created_at FROM tb_outbox ORDER BY id");
    }

    public List<Map<String, Object>> orders() {
        return jdbc.queryForList("SELECT id, user_id, status, coupon_granted FROM tb_order ORDER BY id");
    }

    /**
     * 先插入去重表，再发券，同一个事务。
     * 重复消息抛 DuplicateKeyException，事务回滚（去重行不会留下半截）。
     * 调用方收到这个异常仍然 ACK：这条消息已经处理过了。
     */
    @Transactional
    public void consume(String msgId, long orderId) {
        jdbc.update("INSERT INTO tb_consume_log (msg_id, order_id) VALUES (?, ?)", msgId, orderId);
        int updated = jdbc.update("UPDATE tb_order SET coupon_granted = 1, status = 1 WHERE id = ?", orderId);
        if (updated == 0) {
            throw new IllegalStateException("订单不存在: " + orderId);
        }
    }

    public int consumeCount(String msgId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tb_consume_log WHERE msg_id = ?", Integer.class, msgId);
        return n == null ? 0 : n;
    }

    /** 把已发送的 outbox 改回 0，用来观察“同一条消息再次投递”时消费端的去重 */
    public int resetOutbox(long outboxId) {
        return jdbc.update("UPDATE tb_outbox SET status = 0 WHERE id = ?", outboxId);
    }
}
