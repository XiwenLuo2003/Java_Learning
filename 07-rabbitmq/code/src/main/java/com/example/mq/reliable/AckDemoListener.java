package com.example.mq.reliable;

import com.example.mq.config.RabbitConfig;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 观察手动 ACK：消息头 fail-times 表示前几次故意 nack。
 * requeue=true 时消息回到队尾，redelivered 变为 true；requeue=false 时进入死信队列。
 */
@Component
public class AckDemoListener {

    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    private final List<String> events = new ArrayList<>();

    @RabbitListener(queues = RabbitConfig.ACK_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody());
        int failTimes = message.getMessageProperties().getHeader("fail-times") == null
                ? 0 : ((Number) message.getMessageProperties().getHeader("fail-times")).intValue();
        boolean requeue = Boolean.TRUE.equals(message.getMessageProperties().getHeader("requeue"));
        boolean redelivered = message.getMessageProperties().isRedelivered();
        int n = attempts.merge(body, 1, Integer::sum);
        if (n <= failTimes) {
            record(body + " 第 " + n + " 次投递 redelivered=" + redelivered + " → nack requeue=" + requeue);
            channel.basicNack(tag, false, requeue);
            return;
        }
        record(body + " 第 " + n + " 次投递 redelivered=" + redelivered + " → ack");
        channel.basicAck(tag, false);
    }

    private synchronized void record(String event) {
        events.add(event);
    }

    public synchronized List<String> events() {
        return List.copyOf(events);
    }

    public synchronized void clear() {
        events.clear();
        attempts.clear();
    }
}
