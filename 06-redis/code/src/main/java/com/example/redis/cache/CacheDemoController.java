package com.example.redis.cache;

import com.example.redis.common.Concurrent;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 第 3 课：缓存穿透、击穿、雪崩。请求见 requests.http 第 3 组。
 */
@RestController
@RequestMapping("/cache")
public class CacheDemoController {

    static final String SHOP_KEY = "cache:shop:";
    private static final Duration SHOP_TTL = Duration.ofMinutes(30);
    private static final String STALE_MARK = "（缓存旧值）";

    private final CacheClient cacheClient;
    private final ShopRepository shopRepository;
    private final RedissonClient redisson;
    private final StringRedisTemplate redis;

    public CacheDemoController(CacheClient cacheClient, ShopRepository shopRepository,
                               RedissonClient redisson, StringRedisTemplate redis) {
        this.cacheClient = cacheClient;
        this.shopRepository = shopRepository;
        this.redisson = redisson;
        this.redis = redis;
    }

    /** 正常查询：GET /cache/shop/1，第一次查库，之后命中缓存 */
    @GetMapping("/shop/{id}")
    public Map<String, Object> shop(@PathVariable long id) {
        int before = shopRepository.queryCount();
        Shop shop = cacheClient.queryWithPassThrough(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shop", shop);
        result.put("hitDatabase", shopRepository.queryCount() > before);
        result.put("ttlSeconds", redis.getExpire(SHOP_KEY + id));
        return result;
    }

    /**
     * 实验 1：缓存穿透。查询数据库里根本不存在的店铺，请求一个个发。
     * <ul>
     *   <li>mode=none：每次都打到数据库</li>
     *   <li>mode=nullValue：同一个 id 只查一次库；random=true（每次换 id）时空值缓存失效，还会堆一堆空 key</li>
     *   <li>mode=bloom：布隆过滤器在最前面挡住，random=true 也几乎不查库</li>
     * </ul>
     */
    @GetMapping("/penetration")
    public Map<String, Object> penetration(@RequestParam(defaultValue = "none") String mode,
                                           @RequestParam(defaultValue = "false") boolean random,
                                           @RequestParam(defaultValue = "100") int requests) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            keys.add(SHOP_KEY + idOf(random, i));
        }
        redis.delete(keys);
        RBloomFilter<Long> bloom = "bloom".equals(mode) ? rebuildBloomFilter() : null;
        shopRepository.resetQueryCount();

        int blocked = 0;
        for (int i = 0; i < requests; i++) {
            long id = idOf(random, i);
            switch (mode) {
                case "nullValue" -> cacheClient.queryWithPassThrough(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
                case "bloom" -> {
                    if (!bloom.contains(id)) {
                        blocked++;     // 布隆过滤器说不存在，就一定不存在，直接返回
                        continue;
                    }
                    cacheClient.queryWithPassThrough(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
                }
                default -> cacheClient.queryPlain(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("randomIds", random);
        result.put("requests", requests);
        result.put("dbQueries", shopRepository.queryCount());
        result.put("blockedByBloom", blocked);
        result.put("emptyKeysInRedis", redis.countExistingKeys(keys));
        return result;
    }

    private static long idOf(boolean random, int i) {
        return random ? 100_000L + i : 9999L;
    }

    /**
     * Redisson 的布隆过滤器：位数组存在 Redis 的一个 String 里，配置存在 {bf:shop}:config 这个 Hash 里。
     * 数据库新增店铺时要同步 add；删除店铺没法从布隆过滤器里删，只能定期重建。
     */
    private RBloomFilter<Long> rebuildBloomFilter() {
        RBloomFilter<Long> bloom = redisson.getBloomFilter("bf:shop", LongCodec.INSTANCE);
        bloom.delete();
        bloom.tryInit(100_000, 0.01);
        for (Long id : shopRepository.findAllIds()) {
            bloom.add(id);
        }
        return bloom;
    }

    /**
     * 实验 2：缓存击穿。热点店铺 1 的缓存失效瞬间，requests 个请求同时到达，数据库查询耗时 200ms。
     * <ul>
     *   <li>mode=none：没有保护，几乎每个请求都查库</li>
     *   <li>mode=mutex：只有 1 次查库，其他请求等待，costMs 变长</li>
     *   <li>mode=logical：只有 1 次查库，所有请求立即返回，但拿到的是旧值（staleResponses）</li>
     *   <li>mode=logicalUnlockEarly：黑马点评的写法，锁提前释放，查库次数大于 1</li>
     * </ul>
     */
    @GetMapping("/breakdown")
    public Map<String, Object> breakdown(@RequestParam(defaultValue = "none") String mode,
                                         @RequestParam(defaultValue = "100") int requests) {
        long id = 1L;
        String key = SHOP_KEY + id;
        boolean logical = mode.startsWith("logical");
        if (logical) {
            // 预热一个“逻辑上已经过期”的旧值，名字带标记，方便统计谁拿到了旧数据
            Shop old = shopRepository.findById(id);
            Shop stale = new Shop(old.id(), old.name() + STALE_MARK, old.score());
            cacheClient.setWithLogicalExpire(key, stale, Duration.ofSeconds(-1));
        } else {
            redis.delete(key);
        }

        shopRepository.setSlowMillis(200);
        shopRepository.resetQueryCount();
        long start = System.currentTimeMillis();
        try {
            List<Shop> responses = Concurrent.run(requests, i -> switch (mode) {
                case "mutex" -> cacheClient.queryWithMutex(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
                case "logical" -> cacheClient.queryWithLogicalExpire(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
                case "logicalUnlockEarly" -> cacheClient.queryWithLogicalExpireUnlockEarly(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
                default -> cacheClient.queryPlain(SHOP_KEY, id, Shop.class, shopRepository::findById, SHOP_TTL);
            });
            long cost = System.currentTimeMillis() - start;
            if (logical) {
                Concurrent.sleep(1500);   // 等异步重建完成，把它的查库次数也算进来
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", mode);
            result.put("requests", requests);
            result.put("dbQueries", shopRepository.queryCount());
            result.put("costMs", cost);
            result.put("staleResponses", responses.stream()
                    .filter(Objects::nonNull).filter(s -> s.name().endsWith(STALE_MARK)).count());
            result.put("cacheNow", redis.opsForValue().get(key));
            return result;
        } finally {
            shopRepository.setSlowMillis(0);
        }
    }

    /**
     * 实验 3：缓存雪崩。批量写入 keys 个缓存，统计“同一秒过期的 key 最多有多少个”。
     * jitter=false：全部 TTL 30 分钟，30 分钟后同一秒集体过期；jitter=true：TTL 加 0~5 分钟随机值，过期时间被打散。
     */
    @GetMapping("/avalanche")
    public Map<String, Object> avalanche(@RequestParam(defaultValue = "false") boolean jitter,
                                         @RequestParam(defaultValue = "1000") int keys) {
        List<String> keyList = new ArrayList<>();
        for (int i = 0; i < keys; i++) {
            String key = "demo:avalanche:" + i;
            keyList.add(key);
            if (jitter) {
                cacheClient.setWithRandomTtl(key, i, SHOP_TTL, Duration.ofMinutes(5));
            } else {
                cacheClient.set(key, i, SHOP_TTL);
            }
        }

        // key：剩余秒数，value：这一秒过期的 key 个数
        TreeMap<Long, Integer> histogram = new TreeMap<>();
        for (String key : keyList) {
            histogram.merge(redis.getExpire(key), 1, Integer::sum);
        }
        redis.delete(keyList);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jitter", jitter);
        result.put("keys", keys);
        result.put("distinctExpireSeconds", histogram.size());
        result.put("maxKeysExpiringInSameSecond", histogram.values().stream().mapToInt(Integer::intValue).max().orElse(0));
        return result;
    }
}
