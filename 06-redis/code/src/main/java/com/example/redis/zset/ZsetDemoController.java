package com.example.redis.zset;

import com.example.redis.common.Concurrent;
import com.example.redis.common.Timeline;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 第 2 课：ZSet 三个经典场景。请求见 requests.http 第 2 组。
 */
@RestController
@RequestMapping("/zset")
public class ZsetDemoController {

    private final SlidingWindowRateLimiter sliding;
    private final FixedWindowRateLimiter fixed;
    private final FieldExpireHash fieldExpireHash;
    private final RedisDelayQueue delayQueue;
    private final StringRedisTemplate redis;

    public ZsetDemoController(SlidingWindowRateLimiter sliding, FixedWindowRateLimiter fixed,
                              FieldExpireHash fieldExpireHash, RedisDelayQueue delayQueue,
                              StringRedisTemplate redis) {
        this.sliding = sliding;
        this.fixed = fixed;
        this.fieldExpireHash = fieldExpireHash;
        this.delayQueue = delayQueue;
        this.redis = redis;
    }

    /**
     * 实验 1：滑动窗口 vs 固定窗口。窗口限制 5 次 / 1 秒。
     * <ul>
     *   <li>mode=uniform：1 秒内均匀发 10 次。两种都是前 5 次放行、后 5 次拒绝</li>
     *   <li>mode=burst：第 0.6 秒连发 5 次、第 1.1 秒再连发 5 次。固定窗口两边各算一窗，10 次全放；滑动窗口第二波会被挡住</li>
     * </ul>
     */
    @GetMapping("/rate-limit")
    public Map<String, Object> rateLimit(@RequestParam(defaultValue = "uniform") String mode) {
        String slidingKey = "limit:demo:sliding:" + UUID.randomUUID();
        String fixedPrefix = "limit:demo:fixed:" + UUID.randomUUID();
        Duration window = Duration.ofSeconds(1);
        int limit = 5;

        List<Long> delays = new ArrayList<>();
        if ("burst".equals(mode)) {
            for (int i = 0; i < 5; i++) delays.add(600L);
            for (int i = 0; i < 5; i++) delays.add(1100L);
        } else {
            for (int i = 0; i < 10; i++) delays.add(i * 100L);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < delays.size(); i++) {
            long wait = delays.get(i) - (System.currentTimeMillis() - start);
            if (wait > 0) Concurrent.sleep(wait);
            boolean s = sliding.tryAcquire(slidingKey, window, limit);
            boolean f = fixed.tryAcquire(fixedPrefix, limit);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("atMs", System.currentTimeMillis() - start);
            row.put("sliding", s ? "放行" : "拒绝");
            row.put("fixed", f ? "放行" : "拒绝");
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("rule", "5 次 / 1 秒");
        result.put("requests", rows);
        result.put("slidingMembers", redis.opsForZSet().size(slidingKey));
        return result;
    }

    /**
     * 实验 2：Hash 字段级过期。三张券，TTL 分别是 1s / 3s / 10s。
     * sleepSeconds 秒后读一遍、再 clean 一次，观察谁被惰性删除、谁被定期删除。
     */
    @GetMapping("/field-expire")
    public Map<String, Object> fieldExpire(@RequestParam(defaultValue = "2") int sleepSeconds) {
        String name = "coupons:" + UUID.randomUUID();
        fieldExpireHash.set(name, "c1", "满100减20", Duration.ofSeconds(1));
        fieldExpireHash.set(name, "c2", "满200减50", Duration.ofSeconds(3));
        fieldExpireHash.set(name, "c3", "满500减100", Duration.ofSeconds(10));

        Concurrent.sleep(sleepSeconds * 1000L);

        Map<String, Object> afterGet = new LinkedHashMap<>();
        afterGet.put("c1", fieldExpireHash.get(name, "c1"));   // sleep=2 时已经过期，get 触发惰性删除，返回 null
        afterGet.put("c2", fieldExpireHash.get(name, "c2"));   // 还没过期
        afterGet.put("c3", fieldExpireHash.get(name, "c3"));

        Set<ZSetOperations.TypedTuple<String>> expireBefore = redis.opsForZSet()
                .rangeWithScores(FieldExpireHash.expireKey(name), 0, -1);
        long cleaned = fieldExpireHash.clean(name);            // 没人读的过期字段，靠定期删除清掉

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sleepSeconds", sleepSeconds);
        result.put("afterGet", afterGet);
        result.put("expireZsetBeforeClean", expireBefore);
        result.put("cleanedByPeriodic", cleaned);
        result.put("hashAfterClean", redis.opsForHash().entries(FieldExpireHash.hashKey(name)));
        result.put("expireZsetAfterClean", redis.opsForZSet().rangeWithScores(FieldExpireHash.expireKey(name), 0, -1));
        return result;
    }

    /**
     * 实验 3：延迟队列。两个任务分别延迟 1s / 3s，每 200ms 轮询一次，看谁先被取出。
     * 对比：两个消费者同时 poll，同一个任务只会被一个人拿到（Lua 原子）。
     */
    @GetMapping("/delay-queue")
    public Map<String, Object> delayQueue() {
        String queue = "delay:order:" + UUID.randomUUID();
        Timeline timeline = new Timeline();
        delayQueue.offer(queue, "cancel:order-A", Duration.ofSeconds(1));
        delayQueue.offer(queue, "cancel:order-B", Duration.ofSeconds(3));
        timeline.log("main", "入队 A=1s、B=3s");

        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            String task = delayQueue.poll(queue);
            if (task != null) {
                timeline.log("worker", "取出 " + task);
            }
            Concurrent.sleep(200);
        }

        // 两个消费者并发抢同一个到期任务：只能有一个成功
        String raceQueue = "delay:race:" + UUID.randomUUID();
        delayQueue.offer(raceQueue, "only-once", Duration.ZERO);
        List<String> winners = Concurrent.run(5, i -> delayQueue.poll(raceQueue));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeline", timeline.events());
        result.put("remaining", redis.opsForZSet().rangeWithScores(queue, 0, -1));
        result.put("concurrentPollResults", winners);
        result.put("concurrentWinners", winners.stream().filter(x -> x != null).count());
        return result;
    }
}
