package com.example.redis.lock;

import com.example.redis.common.Concurrent;
import com.example.redis.common.Timeline;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 第 5 课：分布式锁。请求见 requests.http 第 5 组。
 */
@RestController
@RequestMapping("/lock")
public class LockDemoController {

    private static final String COUNTER_KEY = "demo:lock:counter";

    private final SimpleRedisLock simpleLock;
    private final RedissonClient redisson;
    private final StringRedisTemplate redis;

    public LockDemoController(SimpleRedisLock simpleLock, RedissonClient redisson, StringRedisTemplate redis) {
        this.simpleLock = simpleLock;
        this.redisson = redisson;
        this.redis = redis;
    }

    /**
     * 实验 1：锁过期后，A 释放锁会不会误删 B 的锁。
     * <pre>
     * 0ms     A 加锁，ttl = 1s，业务要执行 1200ms（超时了）
     * 1000ms  A 的锁过期
     * 1100ms  B 加锁成功，开始执行业务
     * 1200ms  A 业务结束，释放锁（mode 决定怎么释放）
     * 1400ms  C 来加锁：如果成功，说明 B 的锁被 A 删了，B 和 C 同时在临界区
     * </pre>
     * mode=unsafe：直接 DEL；mode=checkThenDelete：900ms 时 GET 比较通过，卡 300ms 再 DEL；mode=lua：Lua 原子比较并删除。
     */
    @GetMapping("/expire-then-unlock")
    public Map<String, Object> expireThenUnlock(@RequestParam(defaultValue = "unsafe") String mode) {
        String name = "demo:expire";
        simpleLock.unlockUnsafe(name);
        Timeline timeline = new Timeline();
        Map<String, Object> result = new LinkedHashMap<>();

        Concurrent.run(3, i -> {
            try {
                if (i == 0) {
                    String token = simpleLock.tryLock(name, Duration.ofSeconds(1));
                    timeline.log("A", "加锁 " + (token != null ? "成功" : "失败") + "，ttl=1s");
                    switch (mode) {
                        case "unsafe" -> {
                            Thread.sleep(1200);
                            simpleLock.unlockUnsafe(name);
                            timeline.log("A", "业务结束，直接 DEL（不管是不是自己的锁）");
                        }
                        case "checkThenDelete" -> {
                            Thread.sleep(900);
                            timeline.log("A", "业务结束，GET 比较 token：是自己的锁；接着卡顿 300ms（模拟 GC）");
                            boolean deleted = simpleLock.unlockCheckThenDelete(name, token, 300);
                            timeline.log("A", "醒来执行 DEL，deleted=" + deleted + "（此时锁已经是 B 的了）");
                        }
                        default -> {
                            Thread.sleep(1200);
                            boolean released = simpleLock.unlock(name, token);
                            timeline.log("A", "业务结束，Lua 比较并删除，released=" + released + "（token 对不上，不删）");
                        }
                    }
                } else if (i == 1) {
                    Thread.sleep(1100);
                    String token = simpleLock.tryLock(name, Duration.ofSeconds(5));
                    timeline.log("B", "加锁 " + (token != null ? "成功（A 的锁已过期）" : "失败"));
                    Thread.sleep(1000);
                    if (token != null) {
                        timeline.log("B", "业务结束，释放锁 released=" + simpleLock.unlock(name, token));
                    }
                } else {
                    Thread.sleep(1400);
                    String token = simpleLock.tryLock(name, Duration.ofSeconds(5));
                    result.put("cAcquired", token != null);
                    timeline.log("C", token != null
                            ? "加锁成功！B 还没执行完，B 和 C 同时在临界区，锁失效了"
                            : "加锁失败，B 的锁还在，正确");
                    if (token != null) {
                        simpleLock.unlock(name, token);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        result.put("mode", mode);
        result.put("timeline", timeline.events());
        result.put("note", "即使 mode=lua，1100~1200ms 之间 A 的业务还没结束、B 已经拿到锁，两者仍然重叠。"
                + "根本问题是锁的 ttl 比业务短，这就是 Redisson 看门狗要解决的（实验 4）");
        return result;
    }

    /**
     * 实验 2：用“GET 再 SET”的非原子方式累加，对比不加锁、自研锁、Redisson 锁。
     * 真实业务里累加直接用 INCR；这里故意拆成两步，是为了制造一个需要锁保护的临界区。
     */
    @GetMapping("/counter")
    public Map<String, Object> counter(@RequestParam(defaultValue = "none") String mode,
                                       @RequestParam(defaultValue = "10") int threads,
                                       @RequestParam(defaultValue = "50") int times) {
        redis.opsForValue().set(COUNTER_KEY, "0");
        RLock rLock = redisson.getLock("lock:demo:counter:redisson");
        long start = System.currentTimeMillis();

        Concurrent.run(threads, i -> {
            for (int t = 0; t < times; t++) {
                switch (mode) {
                    case "simple" -> {
                        String token;
                        while ((token = simpleLock.tryLock("demo:counter", Duration.ofSeconds(5))) == null) {
                            Concurrent.sleep(1);   // 自研锁没有排队，只能自旋重试
                        }
                        try {
                            incrementNonAtomic();
                        } finally {
                            simpleLock.unlock("demo:counter", token);
                        }
                    }
                    case "redisson" -> {
                        rLock.lock();              // 拿不到会订阅锁释放的消息（Pub/Sub），被唤醒后再抢，不用空转
                        try {
                            incrementNonAtomic();
                        } finally {
                            rLock.unlock();
                        }
                    }
                    default -> incrementNonAtomic();
                }
            }
            return null;
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("expected", threads * times);
        result.put("actual", Integer.parseInt(redis.opsForValue().get(COUNTER_KEY)));
        result.put("costMs", System.currentTimeMillis() - start);
        return result;
    }

    private void incrementNonAtomic() {
        int value = Integer.parseInt(redis.opsForValue().get(COUNTER_KEY));
        redis.opsForValue().set(COUNTER_KEY, String.valueOf(value + 1));
    }

    /**
     * 实验 3：可重入。自研锁同一线程第二次加锁会失败；Redisson 用 Hash 记录“线程标识 → 重入次数”。
     */
    @GetMapping("/reentrant")
    public Map<String, Object> reentrant() {
        Map<String, Object> result = new LinkedHashMap<>();

        String first = simpleLock.tryLock("demo:reentrant", Duration.ofSeconds(30));
        String second = simpleLock.tryLock("demo:reentrant", Duration.ofSeconds(30));
        result.put("simpleFirst", first != null);
        result.put("simpleSecond", second != null);
        simpleLock.unlock("demo:reentrant", first);

        String key = "lock:demo:reentrant:redisson";
        RLock lock = redisson.getLock(key);
        lock.lock();
        lock.lock();
        try {
            result.put("redissonHoldCount", lock.getHoldCount());
            result.put("redissonHash", redis.opsForHash().entries(key));
            result.put("redissonTtlMs", redis.getExpire(key, TimeUnit.MILLISECONDS));
        } finally {
            lock.unlock();
            result.put("afterFirstUnlockHoldCount", lock.getHoldCount());
            result.put("afterFirstUnlockKeyExists", redis.hasKey(key));
            lock.unlock();
            result.put("afterSecondUnlockKeyExists", redis.hasKey(key));
        }
        return result;
    }

    /**
     * 实验 4：看门狗。leaseSeconds=0 时不指定过期时间，Redisson 先设 30s，之后每 10s（30/3）续期回 30s；
     * leaseSeconds>0 时看门狗不生效，到点就过期。请求会持续 holdSeconds 秒。
     */
    @GetMapping("/watchdog")
    public Map<String, Object> watchdog(@RequestParam(defaultValue = "0") long leaseSeconds,
                                        @RequestParam(defaultValue = "35") int holdSeconds) {
        RLock lock = redisson.getLock("lock:demo:watchdog");
        lock.forceUnlock();
        Timeline timeline = new Timeline();

        if (leaseSeconds > 0) {
            lock.lock(leaseSeconds, TimeUnit.SECONDS);
            timeline.log("main", "lock(" + leaseSeconds + "s)：指定了过期时间，看门狗不续期");
        } else {
            lock.lock();
            timeline.log("main", "lock()：没指定过期时间，看门狗接管");
        }
        for (int s = 0; ; s += 5) {
            timeline.log("main", "剩余 TTL = " + lock.remainTimeToLive() + " ms");
            if (s >= holdSeconds) {
                break;
            }
            Concurrent.sleep(5000);
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            timeline.log("main", "业务结束，unlock，看门狗随之停止");
        } else {
            timeline.log("main", "锁已经过期，不再持有。这时调 unlock 会抛 IllegalMonitorStateException");
        }
        return Map.of("leaseSeconds", leaseSeconds, "timeline", timeline.events());
    }
}
