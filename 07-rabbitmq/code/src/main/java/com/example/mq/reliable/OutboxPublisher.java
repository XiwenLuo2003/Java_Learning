package com.example.mq.reliable;

import com.example.mq.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫本地消息表，确认 broker 收到之后才把 status 改成 1。
 * 进程在“事务提交”和“改成 1”之间崩溃，重启后还会再发；消费端靠 msgId 去重。
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbitTemplate;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbitTemplate) {
        this.jdbc = jdbc;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    @Scheduled(fixedDelay = 1000)
    public void flush() {
        if (!enabled.get()) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, order_id FROM tb_outbox WHERE status = 0 ORDER BY id");
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            long orderId = ((Number) row.get("order_id")).longValue();
            publish(id, orderId);
        }
    }

    private void publish(long outboxId, long orderId) {
        String msgId = String.valueOf(outboxId);
        CorrelationData correlation = new CorrelationData(msgId);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.ORDER_EXCHANGE, RabbitConfig.ORDER_KEY,
                    orderId + ":" + msgId, message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setMessageId(msgId);
                        return message;
                    }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            if (confirm != null && confirm.isAck()) {
                jdbc.update("UPDATE tb_outbox SET status = 1 WHERE id = ? AND status = 0", outboxId);
                log.info("outbox {} 已确认投递 orderId={}", outboxId, orderId);
            } else {
                log.warn("outbox {} 未被确认: {}", outboxId, confirm == null ? "timeout" : confirm.getReason());
            }
        } catch (Exception e) {
            log.warn("outbox {} 投递失败，留到下一轮: {}", outboxId, e.getMessage());
        }
    }
}
