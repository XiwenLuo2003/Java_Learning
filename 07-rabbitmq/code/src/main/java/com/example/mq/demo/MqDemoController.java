package com.example.mq.demo;

import com.example.mq.config.RabbitConfig;
import com.example.mq.reliable.AckDemoListener;
import com.example.mq.reliable.OrderCreatedListener;
import com.example.mq.reliable.OrderService;
import com.example.mq.reliable.OutboxPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 全部实验入口。请求见 requests.http。
 */
@RestController
@RequestMapping("/mq")
public class MqDemoController {

    private final RabbitTemplate rabbitTemplate;
    private final OrderService orderService;
    private final OutboxPublisher outboxPublisher;
    private final OrderCreatedListener orderListener;
    private final AckDemoListener ackListener;

    public MqDemoController(RabbitTemplate rabbitTemplate, OrderService orderService, OutboxPublisher outboxPublisher,
                            OrderCreatedListener orderListener, AckDemoListener ackListener) {
        this.rabbitTemplate = rabbitTemplate;
        this.orderService = orderService;
        this.outboxPublisher = outboxPublisher;
        this.orderListener = orderListener;
        this.ackListener = ackListener;
    }

    /**
     * 实验 1：三种交换机。
     * type=direct，routingKey=order 才进队列；type=fanout 两个队列都收到；
     * type=topic，routingKey=order.pay 同时匹配 order.# 和 *.pay。
     */
    @GetMapping("/route")
    public Map<String, Object> route(@RequestParam(defaultValue = "direct") String type,
                                     @RequestParam(defaultValue = "order") String routingKey) {
        String exchange;
        List<String> queues;
        switch (type) {
            case "fanout" -> {
                exchange = RabbitConfig.FANOUT;
                queues = List.of(RabbitConfig.FANOUT_Q1, RabbitConfig.FANOUT_Q2);
            }
            case "topic" -> {
                exchange = RabbitConfig.TOPIC;
                queues = List.of(RabbitConfig.TOPIC_ORDER, RabbitConfig.TOPIC_PAY);
            }
            default -> {
                exchange = RabbitConfig.DIRECT;
                queues = List.of(RabbitConfig.DIRECT_QUEUE);
            }
        }
        queues.forEach(this::purge);
        String body = "msg-" + System.currentTimeMillis();
        rabbitTemplate.convertAndSend(exchange, routingKey, body);

        Map<String, String> received = new LinkedHashMap<>();
        for (String queue : queues) {
            Message message = rabbitTemplate.receive(queue, 800);
            received.put(queue, message == null ? null : new String(message.getBody(), StandardCharsets.UTF_8));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("exchange", exchange);
        result.put("routingKey", routingKey);
        result.put("sent", body);
        result.put("received", received);
        return result;
    }

    /**
     * 实验 2：TTL + 死信 = 延迟消息。和 mall 的 CancelOrderSender 一样，用单条消息的 expiration。
     * 请求会阻塞到消息出现在 delay.ready（或超时）。
     */
    @GetMapping("/delay")
    public Map<String, Object> delay(@RequestParam(defaultValue = "2000") long ttlMs) {
        purge(RabbitConfig.DELAY_TTL_QUEUE);
        purge(RabbitConfig.DELAY_READY_QUEUE);
        long sentAt = System.currentTimeMillis();
        sendDelayed("cancel-order-1", ttlMs);
        Message message = rabbitTemplate.receive(RabbitConfig.DELAY_READY_QUEUE, ttlMs + 3000);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ttlMs", ttlMs);
        result.put("arrivedAfterMs", message == null ? null : System.currentTimeMillis() - sentAt);
        result.put("body", message == null ? null : new String(message.getBody(), StandardCharsets.UTF_8));
        return result;
    }

    /**
     * 实验 3：按消息设置 TTL 的坑。队列只检查队头。
     * 先发一条 5 秒过期的，再发一条 0.5 秒过期的：短的那条要等长的离开队头才会被检查，所以它也是约 5 秒后才到。
     */
    @GetMapping("/delay/head-block")
    public Map<String, Object> headBlock() {
        purge(RabbitConfig.DELAY_TTL_QUEUE);
        purge(RabbitConfig.DELAY_READY_QUEUE);
        long sentAt = System.currentTimeMillis();
        sendDelayed("slow-5s", 5000);
        sendDelayed("fast-0.5s", 500);
        List<Map<String, Object>> arrived = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Message message = rabbitTemplate.receive(RabbitConfig.DELAY_READY_QUEUE, 8000);
            if (message == null) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("body", new String(message.getBody(), StandardCharsets.UTF_8));
            row.put("arrivedAfterMs", System.currentTimeMillis() - sentAt);
            arrived.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("arrived", arrived);
        result.put("note", "两条都在 5 秒左右到达，且 slow-5s 在前。要每条消息按自己的 TTL 准时投递，用 rabbitmq-delayed-message-exchange 插件，或保证队列里 TTL 单调不减");
        return result;
    }

    /**
     * 实验 4：Publisher Confirm 和 mandatory return。
     * mode=ok 交换机确认；mode=no-exchange 交换机不存在，confirm 为 nack；
     * mode=unroutable 交换机在、路由键没有队列，confirm 仍是 ack，同时消息被退回。
     */
    @GetMapping("/confirm")
    public Map<String, Object> confirm(@RequestParam(defaultValue = "ok") String mode) throws Exception {
        String exchange = RabbitConfig.DIRECT;
        String routingKey = RabbitConfig.DIRECT_KEY;
        if ("no-exchange".equals(mode)) {
            exchange = "missing.exchange";
        } else if ("unroutable".equals(mode)) {
            routingKey = "no.such.key";
        }
        CorrelationData correlation = new CorrelationData(mode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("exchange", exchange);
        result.put("routingKey", routingKey);
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, "confirm-" + mode, correlation);
            CorrelationData.Confirm broker = correlation.getFuture().get(3, TimeUnit.SECONDS);
            Thread.sleep(200);
            result.put("ack", broker != null && broker.isAck());
            result.put("reason", broker == null ? null : broker.getReason());
            result.put("returned", correlation.getReturned() == null ? null : correlation.getReturned().getReplyText());
        } catch (Exception e) {
            result.put("ack", false);
            result.put("reason", e.getMessage());
            result.put("returned", null);
        }
        return result;
    }

    /** 实验 5：下单。只写本地事务，消息由发布器异步发出 */
    @GetMapping("/order")
    public Map<String, Object> createOrder(@RequestParam(defaultValue = "1") long userId) {
        return orderService.create(userId);
    }

    @GetMapping("/order/publisher")
    public Map<String, Object> publisher(@RequestParam boolean enabled) {
        outboxPublisher.setEnabled(enabled);
        return Map.of("publisherEnabled", outboxPublisher.isEnabled());
    }

    @GetMapping("/order/outbox")
    public Map<String, Object> outbox() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publisherEnabled", outboxPublisher.isEnabled());
        result.put("outbox", orderService.outbox());
        result.put("orders", orderService.orders());
        result.put("consumed", orderListener.handled());
        result.put("duplicates", orderListener.duplicates());
        return result;
    }

    /** 实验 6：把同一条 outbox 再投递一次。发券次数不增加，duplicates +1 */
    @GetMapping("/order/replay")
    public Map<String, Object> replay(@RequestParam long outboxId) throws InterruptedException {
        int before = orderListener.duplicates();
        int reset = orderService.resetOutbox(outboxId);
        Thread.sleep(1500);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resetRows", reset);
        result.put("duplicatesBefore", before);
        result.put("duplicatesAfter", orderListener.duplicates());
        result.put("orders", orderService.orders());
        result.put("consumeLogRows", orderService.consumeCount(String.valueOf(outboxId)));
        return result;
    }

    /**
     * 实验 7：手动 ACK。
     * mode=requeue：第一次 nack 并重新入队，第二次 ack，redelivered 从 false 变成 true。
     * mode=dlq：nack 且不入队，消息出现在 demo.ack.dlq，原队列不再投递。
     */
    @GetMapping("/ack")
    public Map<String, Object> ack(@RequestParam(defaultValue = "requeue") String mode) throws InterruptedException {
        ackListener.clear();
        purge(RabbitConfig.ACK_DLQ);
        String body = "task-" + System.currentTimeMillis();
        boolean requeue = !"dlq".equals(mode);
        int failTimes = 1;
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("fail-times", failTimes)
                .setHeader("requeue", requeue)
                .build();
        rabbitTemplate.send(RabbitConfig.ACK_EXCHANGE, RabbitConfig.ACK_KEY, message);
        Thread.sleep(requeue ? 800 : 400);
        Message dead = rabbitTemplate.receive(RabbitConfig.ACK_DLQ, 300);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("events", ackListener.events());
        result.put("dlqBody", dead == null ? null : new String(dead.getBody(), StandardCharsets.UTF_8));
        return result;
    }

    private void sendDelayed(String body, long ttlMs) {
        rabbitTemplate.convertAndSend(RabbitConfig.DELAY_EXCHANGE, RabbitConfig.DELAY_TTL_KEY, body, message -> {
            message.getMessageProperties().setExpiration(String.valueOf(ttlMs));
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
    }

    private void purge(String queue) {
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(queue);
            return null;
        });
    }
}
