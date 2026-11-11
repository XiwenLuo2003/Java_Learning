package com.example.redis.zset;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * ZSet 滑动窗口限流：每个请求是 ZSet 里的一个成员，score 是请求时间。
 * “删旧记录 → 计数 → 写入”三步放在 Lua 里原子执行，否则两个请求同时 ZCARD 都看到 limit-1，会一起放行。
 */
@Component
public class SlidingWindowRateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("lua/sliding_window.lua"));
        SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public SlidingWindowRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(String key, Duration window, int limit) {
        Long result = redis.execute(SCRIPT, List.of(key),
                String.valueOf(window.toMillis()), String.valueOf(limit), UUID.randomUUID().toString());
        return result != null && result == 1L;
    }
}
