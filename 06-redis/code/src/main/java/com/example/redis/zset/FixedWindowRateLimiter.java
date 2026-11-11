package com.example.redis.zset;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 固定窗口计数器限流（对照组）：每一秒一个 key，INCR 计数。
 * 实现简单、只占一个整数，但有“临界突刺”：第 0.9 秒来 limit 个、第 1.1 秒再来 limit 个，
 * 两个窗口各自都没超限，可 0.2 秒内实际放过了 2 倍的请求。
 */
@Component
public class FixedWindowRateLimiter {

    private final StringRedisTemplate redis;

    public FixedWindowRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String keyPrefix, int limitPerSecond) {
        String key = keyPrefix + ":" + (System.currentTimeMillis() / 1000);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // INCR 和 EXPIRE 是两条命令：如果在这中间进程挂了，key 永不过期。生产环境要合进 Lua 里
            redis.expire(key, Duration.ofSeconds(2));
        }
        return count != null && count <= limitPerSecond;
    }
}
