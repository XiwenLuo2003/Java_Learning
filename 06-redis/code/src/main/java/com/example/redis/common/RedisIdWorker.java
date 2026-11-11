package com.example.redis.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一 ID，照搬黑马点评 utils/RedisIdWorker。
 * <pre>
 * | 1 位符号 | 31 位秒级时间戳（相对 2022-01-01） | 32 位当天序列号（INCR） |
 * </pre>
 * 和雪花算法的区别：序列号靠 Redis INCR 保证全局唯一，不依赖机器号，也不怕时钟回拨造成重复（同一秒内序列号仍然递增）。
 * key 按天拆分，一是单个 key 的 32 位序列号够用，二是方便统计每天的订单量。
 */
@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate redis;

    public RedisIdWorker(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = redis.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }
}
