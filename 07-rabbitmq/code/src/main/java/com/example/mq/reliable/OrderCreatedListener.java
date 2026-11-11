package com.example.mq.reliable;

import com.example.mq.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手动 ACK：业务事务提交之后才 basicAck。
 * mall 的 CancelOrderReceiver 用的是默认自动 ACK，方法抛异常时消息已经被确认，取消订单会丢。
 */
@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final OrderService orderService;
    private final AtomicInteger handled = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();

    public OrderCreatedListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitConfig.ORDER_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody());
        String[] parts = body.split(":");
        long orderId = Long.parseLong(parts[0]);
        String msgId = parts[1];
        try {
            orderService.consume(msgId, orderId);
            channel.basicAck(tag, false);
            handled.incrementAndGet();
            log.info("发券成功 orderId={} msgId={}", orderId, msgId);
        } catch (DuplicateKeyException e) {
            channel.basicAck(tag, false);
            duplicates.incrementAndGet();
            log.info("重复消息，已 ACK 跳过 orderId={} msgId={}", orderId, msgId);
        } catch (Exception e) {
            log.warn("消费失败，重新入队 orderId={} : {}", orderId, e.getMessage());
            channel.basicNack(tag, false, true);
        }
    }

    public int handled() {
        return handled.get();
    }

    public int duplicates() {
        return duplicates.get();
    }
}
