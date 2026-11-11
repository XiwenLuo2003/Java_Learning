package com.example.redis.datatype;

import com.example.redis.common.RedisIdWorker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第 1 课：5 种基础类型的常用命令 + RedisIdWorker + BitMap 签到。
 * 请求见 requests.http 第 1 组。底层结构（SDS、跳表）见 principle 包的 main。
 */
@RestController
@RequestMapping("/datatype")
public class DatatypeDemoController {

    private final StringRedisTemplate redis;
    private final RedisIdWorker idWorker;

    public DatatypeDemoController(StringRedisTemplate redis, RedisIdWorker idWorker) {
        this.redis = redis;
        this.idWorker = idWorker;
    }

    /** String：缓存对象、计数器、分布式锁的载体。底层是 SDS（见 MiniSds） */
    @GetMapping("/string")
    public Map<String, Object> string() {
        redis.opsForValue().set("demo:str:name", "103茶餐厅", Duration.ofMinutes(10));
        redis.opsForValue().set("demo:str:stock", "100");
        Long after = redis.opsForValue().decrement("demo:str:stock");   // 秒杀预扣库存就是 DECR / INCRBY
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("GET demo:str:name", redis.opsForValue().get("demo:str:name"));
        result.put("DECR 后的库存", after);
        result.put("SETNX 第一次", redis.opsForValue().setIfAbsent("demo:str:lock", "token", Duration.ofSeconds(5)));
        result.put("SETNX 第二次", redis.opsForValue().setIfAbsent("demo:str:lock", "token", Duration.ofSeconds(5)));
        result.put("TTL", redis.getExpire("demo:str:name"));
        return result;
    }

    /** Hash：对象的多个字段。购物车、用户会话常用。整个 key 才能 EXPIRE，字段级过期见 /zset/field-expire */
    @GetMapping("/hash")
    public Map<String, Object> hash() {
        String key = "demo:hash:shop:1";
        redis.delete(key);
        redis.opsForHash().put(key, "name", "103茶餐厅");
        redis.opsForHash().put(key, "score", "37");
        redis.opsForHash().put(key, "open", "true");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("HGETALL", redis.opsForHash().entries(key));
        result.put("HINCRBY score 1", redis.opsForHash().increment(key, "score", 1));
        result.put("HMGET name score", redis.opsForHash().multiGet(key, List.of("name", "score")));
        return result;
    }

    /** List：消息队列（简陋版）、最新列表。LPUSH + RPOP = 队列，LPUSH + LPOP = 栈 */
    @GetMapping("/list")
    public Map<String, Object> list() {
        String key = "demo:list:feed";
        redis.delete(key);
        redis.opsForList().leftPushAll(key, "blog-3", "blog-2", "blog-1");   // 左边是最新
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("LRANGE 0 1（最新两条）", redis.opsForList().range(key, 0, 1));
        result.put("RPOP（从尾部取，最早的）", redis.opsForList().rightPop(key));
        result.put("剩下", redis.opsForList().range(key, 0, -1));
        return result;
    }

    /** Set：去重集合。共同关注 = SINTER，点赞用户 = SADD，秒杀一人一单 = SISMEMBER */
    @GetMapping("/set")
    public Map<String, Object> set() {
        redis.delete(List.of("demo:set:follow:me", "demo:set:follow:you"));
        redis.opsForSet().add("demo:set:follow:me", "1", "2", "3", "4");
        redis.opsForSet().add("demo:set:follow:you", "3", "4", "5", "6");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("我的关注", redis.opsForSet().members("demo:set:follow:me"));
        result.put("你的关注", redis.opsForSet().members("demo:set:follow:you"));
        result.put("共同关注 SINTER", redis.opsForSet().intersect("demo:set:follow:me", "demo:set:follow:you"));
        result.put("SISMEMBER 3", redis.opsForSet().isMember("demo:set:follow:me", "3"));
        return result;
    }

    /** ZSet：排行榜、滑动窗口、延迟队列。底层跳表见 MiniSkipList */
    @GetMapping("/zset")
    public Map<String, Object> zset() {
        String key = "demo:zset:rank";
        redis.delete(key);
        redis.opsForZSet().add(key, "amy", 99);
        redis.opsForZSet().add(key, "jack", 95);
        redis.opsForZSet().add(key, "lily", 95);
        redis.opsForZSet().add(key, "tom", 88);
        Set<ZSetOperations.TypedTuple<String>> top3 = redis.opsForZSet().reverseRangeWithScores(key, 0, 2);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ZREVRANGE 0 2 WITHSCORES", top3);
        result.put("ZRANK jack（正序，从 0）", redis.opsForZSet().rank(key, "jack"));
        result.put("ZSCORE lily", redis.opsForZSet().score(key, "lily"));
        result.put("ZRANGEBYSCORE 90 100", redis.opsForZSet().rangeByScoreWithScores(key, 90, 100));
        return result;
    }

    /** BitMap：签到。一年 365 bit ≈ 46 字节，比每天一个 key 省太多 */
    @GetMapping("/bitmap")
    public Map<String, Object> bitmap() {
        String key = "demo:sign:u1001:" + LocalDate.now().getYear();
        redis.delete(key);
        // 假设今天是一年中的第 N 天（0-based），签到就是把这一位置 1
        int dayOfYear = LocalDate.now().getDayOfYear() - 1;
        redis.opsForValue().setBit(key, dayOfYear, true);
        redis.opsForValue().setBit(key, Math.max(0, dayOfYear - 1), true);
        redis.opsForValue().setBit(key, Math.max(0, dayOfYear - 2), true);

        long continuous = 0;
        for (int i = dayOfYear; i >= 0; i--) {
            Boolean bit = redis.opsForValue().getBit(key, i);
            if (Boolean.TRUE.equals(bit)) continuous++;
            else break;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todaySigned", redis.opsForValue().getBit(key, dayOfYear));
        result.put("continuousDays", continuous);
        result.put("bitCount", redis.execute(connection -> connection.stringCommands().bitCount(key.getBytes())));
        return result;
    }

    /** RedisIdWorker：生成 5 个全局唯一 ID，看高 31 位时间戳相同、低 32 位序列号递增 */
    @GetMapping("/id")
    public Map<String, Object> id() {
        long[] ids = new long[5];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = idWorker.nextId("demo");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ids", ids);
        result.put("timestampPart", (ids[0] >>> 32));
        result.put("sequenceParts", new long[]{ids[0] & 0xFFFFFFFFL, ids[1] & 0xFFFFFFFFL, ids[4] & 0xFFFFFFFFL});
        result.put("todayIncrKey", redis.opsForValue().get("icr:demo:" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd"))));
        return result;
    }
}
