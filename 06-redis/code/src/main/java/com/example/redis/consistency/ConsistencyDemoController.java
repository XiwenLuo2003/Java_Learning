package com.example.redis.consistency;

import com.example.redis.cache.Shop;
import com.example.redis.cache.ShopRepository;
import com.example.redis.common.Concurrent;
import com.example.redis.common.Timeline;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第 4 课：缓存与数据库双写一致性。请求见 requests.http 第 4 组。
 * <p>
 * 场景：店铺 2 的评分从 1 改成 2。写线程 W 负责更新，读线程 R 按旁路缓存读取（未命中 → 查库 → 写缓存）。
 * 用 sleep 精确控制两个线程的先后顺序，把“偶尔才出现”的并发问题稳定复现出来。
 * 缓存里只存评分这一个数字，方便观察。
 */
@RestController
@RequestMapping("/consistency")
public class ConsistencyDemoController {

    private static final long SHOP_ID = 2L;
    private static final String KEY = "cache:shop:score:" + SHOP_ID;

    private final ShopRepository shopRepository;
    private final TxShopWriter txShopWriter;
    private final StringRedisTemplate redis;

    public ConsistencyDemoController(ShopRepository shopRepository, TxShopWriter txShopWriter, StringRedisTemplate redis) {
        this.shopRepository = shopRepository;
        this.txShopWriter = txShopWriter;
        this.redis = redis;
    }

    /**
     * strategy 可选：
     * <ul>
     *   <li>deleteThenUpdate：先删缓存再更新库。R 在“删缓存”和“更新库”之间读，把旧值写回缓存 → 不一致</li>
     *   <li>updateThenDelete：先更新库再删缓存（推荐）。正常时序下一致</li>
     *   <li>updateThenDeleteRare：先更新库再删缓存的极端时序：R 查到旧值后卡住，W 更新并删完缓存，R 才把旧值写回 → 不一致</li>
     *   <li>delayDoubleDelete：先删缓存、更新库，过一会儿再删一次，把 R 写回的旧值删掉 → 一致</li>
     *   <li>txDeleteInside：事务内删缓存（黑马点评 ShopServiceImpl#update 的写法），提交前 R 读到未提交前的旧值写回 → 不一致</li>
     *   <li>txDeleteAfterCommit：事务提交后再删缓存 → 一致</li>
     * </ul>
     */
    @GetMapping("/race")
    public Map<String, Object> race(@RequestParam(defaultValue = "deleteThenUpdate") String strategy) {
        shopRepository.updateScore(SHOP_ID, 1);
        redis.delete(KEY);
        if ("updateThenDelete".equals(strategy)) {
            redis.opsForValue().set(KEY, "1", Duration.ofMinutes(30));   // 正常场景：缓存里已经有旧值
        }
        Timeline t = new Timeline();

        switch (strategy) {
            case "deleteThenUpdate" -> Concurrent.run(2, i -> {
                if (i == 0) {
                    deleteCache(t);
                    Concurrent.sleep(300);              // 更新数据库前的耗时（业务校验、网络等）
                    updateDb(t);
                } else {
                    Concurrent.sleep(100);
                    readThrough(t, 0);
                }
                return null;
            });
            case "updateThenDelete" -> Concurrent.run(2, i -> {
                if (i == 0) {
                    Concurrent.sleep(300);
                    updateDb(t);
                    deleteCache(t);
                } else {
                    Concurrent.sleep(100);
                    readThrough(t, 0);                   // 读到缓存里的旧值 1：更新还没发生，这不算不一致
                }
                return null;
            });
            case "updateThenDeleteRare" -> Concurrent.run(2, i -> {
                if (i == 0) {
                    Concurrent.sleep(100);
                    updateDb(t);
                    deleteCache(t);
                } else {
                    readThrough(t, 500);                 // 查库拿到旧值后卡 500ms，比 W 的“更新库 + 删缓存”还慢
                }
                return null;
            });
            case "delayDoubleDelete" -> Concurrent.run(2, i -> {
                if (i == 0) {
                    deleteCache(t);
                    Concurrent.sleep(300);
                    updateDb(t);
                    Concurrent.sleep(500);              // 延迟时间要大于“一次读请求查库 + 写缓存”的耗时
                    t.log("W", "延迟 500ms 后第二次删除");
                    deleteCache(t);
                } else {
                    Concurrent.sleep(100);
                    readThrough(t, 0);
                }
                return null;
            });
            case "txDeleteInside" -> txShopWriter.updateThenDeleteInsideTx(SHOP_ID, 2, KEY, () -> {
                t.log("W", "事务内：UPDATE score=2（未提交），DEL 缓存");
                readInAnotherThread(t);
                t.log("W", "提交事务");
            });
            case "txDeleteAfterCommit" -> txShopWriter.updateThenDeleteAfterCommit(SHOP_ID, 2, KEY, () -> {
                t.log("W", "事务内：UPDATE score=2（未提交），注册 afterCommit 回调");
                readInAnotherThread(t);
                t.log("W", "提交事务，afterCommit 里 DEL 缓存");
            });
            default -> throw new IllegalArgumentException("未知 strategy：" + strategy);
        }

        Shop shop = shopRepository.findById(SHOP_ID);
        String cached = redis.opsForValue().get(KEY);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("strategy", strategy);
        result.put("timeline", t.events());
        result.put("dbScore", shop.score());
        result.put("cacheScore", cached);
        result.put("consistent", cached == null || cached.equals(String.valueOf(shop.score())));
        result.put("note", cached == null ? "缓存为空，下一次读会回源拿到最新值" : "缓存里的值会一直错到 TTL（30 分钟）过期");
        return result;
    }

    private void deleteCache(Timeline t) {
        redis.delete(KEY);
        t.log("W", "DEL 缓存");
    }

    private void updateDb(Timeline t) {
        shopRepository.updateScore(SHOP_ID, 2);
        t.log("W", "UPDATE 数据库 score=2");
    }

    /** 旁路缓存的读：命中直接返回；未命中查库，（可选卡顿后）写回缓存 */
    private void readThrough(Timeline t, long pauseBeforeWrite) {
        String cached = redis.opsForValue().get(KEY);
        if (cached != null) {
            t.log("R", "命中缓存 score=" + cached);
            return;
        }
        Shop shop = shopRepository.findById(SHOP_ID);
        t.log("R", "缓存未命中，查数据库 score=" + shop.score());
        if (pauseBeforeWrite > 0) {
            t.log("R", "卡顿 " + pauseBeforeWrite + "ms（GC、网络抖动）");
            Concurrent.sleep(pauseBeforeWrite);
        }
        redis.opsForValue().set(KEY, String.valueOf(shop.score()), Duration.ofMinutes(30));
        t.log("R", "写缓存 score=" + shop.score());
    }

    private void readInAnotherThread(Timeline t) {
        Thread reader = new Thread(() -> readThrough(t, 0));
        reader.start();
        try {
            reader.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
