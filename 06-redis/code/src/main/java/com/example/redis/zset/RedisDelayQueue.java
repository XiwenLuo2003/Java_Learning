package com.example.redis.zset;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * ZSet 延迟队列：score = 执行时刻。典型场景：订单 30 分钟未支付自动取消。
 * <p>
 * 局限：消费者要轮询（没有到期通知）；任务取出后消费者挂了就丢了（没有 ACK）。
 * 对可靠性要求高时用 RabbitMQ 死信队列 / 延迟插件，或 Redisson 的 RDelayedQueue（也是 ZSet + 定时搬运到 List）。
 */
@Component
public class RedisDelayQueue {

    private static final DefaultRedisScript<String> POLL_SCRIPT = new DefaultRedisScript<>();

    static {
        POLL_SCRIPT.setLocation(new ClassPathResource("lua/delay_queue_poll.lua"));
        POLL_SCRIPT.setResultType(String.class);
    }

    private final StringRedisTemplate redis;

    public RedisDelayQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void offer(String queue, String task, Duration delay) {
        redis.opsForZSet().add(queue, task, System.currentTimeMillis() + delay.toMillis());
    }

    /** 取一个到期任务，没有返回 null */
    public String poll(String queue) {
        return redis.execute(POLL_SCRIPT, List.of(queue), String.valueOf(System.currentTimeMillis()));
    }
}
