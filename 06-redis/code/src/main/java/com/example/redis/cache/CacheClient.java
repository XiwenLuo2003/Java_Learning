package com.example.redis.cache;

import com.example.redis.common.Concurrent;
import com.example.redis.lock.SimpleRedisLock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;

/**
 * 缓存工具类，结构对应黑马点评 utils/CacheClient，修正了其中两个问题（见各方法注释）。
 * <p>
 * 约定：缓存里存 "" 表示“数据库里也没有”（空值缓存）；key 不存在（GET 返回 null）表示没缓存。
 */
@Component
public class CacheClient {

    private static final Logger log = LoggerFactory.getLogger(CacheClient.class);

    /** 空值只缓存 2 分钟：数据库后来有了这条数据，最多 2 分钟后能查到 */
    public static final Duration NULL_TTL = Duration.ofMinutes(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SimpleRedisLock lock;

    public CacheClient(StringRedisTemplate redis, ObjectMapper mapper, SimpleRedisLock lock) {
        this.redis = redis;
        this.mapper = mapper;
        this.lock = lock;
    }

    public void set(String key, Object value, Duration ttl) {
        redis.opsForValue().set(key, toJson(value), ttl);
    }

    /** 防雪崩：过期时间加一个随机值，让同一批写入的 key 不在同一时刻过期 */
    public void setWithRandomTtl(String key, Object value, Duration ttl, Duration maxJitter) {
        long jitterMillis = ThreadLocalRandom.current().nextLong(maxJitter.toMillis() + 1);
        set(key, value, ttl.plusMillis(jitterMillis));
    }

    /** 逻辑过期：Redis 里不设 TTL（永不过期），过期时间写在 value 里，由业务判断 */
    public void setWithLogicalExpire(String key, Object value, Duration ttl) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("expireAt", System.currentTimeMillis() + ttl.toMillis());
        wrapper.put("data", value);
        redis.opsForValue().set(key, toJson(wrapper));
    }

    /** 不做任何防护的旁路缓存（Cache Aside）：查缓存 → 未命中查库 → 写缓存。数据库里没有的不缓存 */
    public <R> R queryPlain(String keyPrefix, long id, Class<R> type, LongFunction<R> dbFallback, Duration ttl) {
        String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);
        if (json != null && !json.isEmpty()) {
            return fromJson(json, type);
        }
        R r = dbFallback.apply(id);
        if (r != null) {
            set(key, r, ttl);
        }
        return r;
    }

    /**
     * 防穿透：缓存空值。数据库查不到也往 Redis 写一个 ""，下次同样的 id 直接在 Redis 挡住。
     * 局限：攻击者每次换一个不存在的 id，空值缓存就没用了，还会在 Redis 里堆一堆垃圾 key → 用布隆过滤器。
     */
    public <R> R queryWithPassThrough(String keyPrefix, long id, Class<R> type, LongFunction<R> dbFallback, Duration ttl) {
        String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);
        if (json != null) {
            return json.isEmpty() ? null : fromJson(json, type);
        }
        R r = dbFallback.apply(id);
        if (r == null) {
            redis.opsForValue().set(key, "", NULL_TTL);
            return null;
        }
        set(key, r, ttl);
        return r;
    }

    /**
     * 防击穿方案一：互斥锁。缓存未命中时只放一个线程去查库重建，其他线程等一会儿再查缓存。
     * 保证一致性，代价是等待；锁的 key 是 lock:{缓存 key}。
     * 和黑马点评的区别：失败重试用循环而不是递归（递归在长时间拿不到锁时可能栈溢出），释放锁校验 token。
     */
    public <R> R queryWithMutex(String keyPrefix, long id, Class<R> type, LongFunction<R> dbFallback, Duration ttl) {
        String key = keyPrefix + id;
        while (true) {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return json.isEmpty() ? null : fromJson(json, type);
            }
            String token = lock.tryLock(key, LOCK_TTL);
            if (token == null) {
                Concurrent.sleep(50);
                continue;
            }
            try {
                // 双重检查：拿到锁时，前一个持锁线程可能已经把缓存重建好了
                json = redis.opsForValue().get(key);
                if (json != null) {
                    return json.isEmpty() ? null : fromJson(json, type);
                }
                R r = dbFallback.apply(id);
                if (r == null) {
                    redis.opsForValue().set(key, "", NULL_TTL);
                    return null;
                }
                set(key, r, ttl);
                return r;
            } finally {
                lock.unlock(key, token);
            }
        }
    }

    /**
     * 防击穿方案二：逻辑过期。key 永不过期，发现逻辑时间过期后，抢到锁的线程交给线程池异步重建，
     * 所有线程（包括抢到锁的）都先返回旧数据。保证可用性，代价是短时间内读到旧数据。
     * <p>
     * 修正了黑马点评 CacheClient 的 bug：那里在提交异步任务后，外层 finally 马上 unLock，
     * 锁只保护了“提交任务”这一瞬间，重建期间其他线程照样能拿到锁、重复提交重建（见 queryWithLogicalExpireUnlockEarly）。
     * 这里锁只在异步任务的 finally 里释放，所以 token 必须能跨线程传递。
     * <p>
     * 前提：热点 key 要提前预热。没命中直接返回 null，说明它不是热点数据（真实项目里可以降级走互斥锁方案）。
     */
    public <R> R queryWithLogicalExpire(String keyPrefix, long id, Class<R> type, LongFunction<R> dbFallback, Duration ttl) {
        String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        LogicalValue<R> cached = readLogical(json, type);
        if (!cached.expired()) {
            return cached.data();
        }
        String token = lock.tryLock(key, LOCK_TTL);
        if (token != null) {
            // 双重检查：拿到锁时，前一个持锁线程可能已经重建完了
            String latestJson = redis.opsForValue().get(key);
            if (latestJson != null && !latestJson.isEmpty()) {
                LogicalValue<R> latest = readLogical(latestJson, type);
                if (!latest.expired()) {
                    lock.unlock(key, token);
                    return latest.data();
                }
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R fresh = dbFallback.apply(id);
                    if (fresh != null) {
                        setWithLogicalExpire(key, fresh, ttl);
                    } else {
                        redis.delete(key);
                    }
                } catch (Exception e) {
                    log.error("缓存重建失败 key={}", key, e);
                } finally {
                    lock.unlock(key, token);
                }
            });
        }
        return cached.data();
    }

    /** 复现黑马点评 CacheClient 第 189~191 行的写法：提交异步任务后，外层 finally 立刻释放锁 */
    public <R> R queryWithLogicalExpireUnlockEarly(String keyPrefix, long id, Class<R> type, LongFunction<R> dbFallback, Duration ttl) {
        String key = keyPrefix + id;
        String json = redis.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return null;
        }
        LogicalValue<R> cached = readLogical(json, type);
        if (!cached.expired()) {
            return cached.data();
        }
        String token = lock.tryLock(key, LOCK_TTL);
        if (token != null) {
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    try {
                        R fresh = dbFallback.apply(id);
                        if (fresh != null) {
                            setWithLogicalExpire(key, fresh, ttl);
                        }
                    } finally {
                        lock.unlock(key, token);
                    }
                });
            } finally {
                lock.unlock(key, token);
            }
        }
        return cached.data();
    }

    private record LogicalValue<R>(long expireAt, R data) {
        boolean expired() {
            return expireAt <= System.currentTimeMillis();
        }
    }

    private <R> LogicalValue<R> readLogical(String json, Class<R> type) {
        try {
            JsonNode root = mapper.readTree(json);
            return new LogicalValue<>(root.get("expireAt").asLong(), mapper.treeToValue(root.get("data"), type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <R> R fromJson(String json, Class<R> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
