package com.example.redis.principle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Redis 的“近似 LRU”（源码 src/evict.c）和标准 LRU 的命中率对比。
 * <p>
 * 标准 LRU 要一条双向链表，每次访问都把节点挪到表头：每个 key 多两个指针，访问还要改链表。
 * Redis 的做法：每个对象头（redisObject）里有 24 位的 lru 字段记录最近访问时间；内存超了就随机抽
 * maxmemory-samples（默认 5）个 key，淘汰其中最久没访问的那个。Redis 3.0 起还加了一个 16 格的“淘汰候选池”，
 * 每轮抽样的结果和池子里的比较，效果更接近标准 LRU。这里只模拟抽样，不模拟候选池。
 * <p>
 * 直接运行 main，不需要 Redis。
 */
public class ApproxLruDemo {

    interface Cache {
        boolean access(int key);
    }

    /** 标准 LRU：LinkedHashMap 的 accessOrder 模式 */
    static Cache exactLru(int capacity) {
        LinkedHashMap<Integer, Boolean> map = new LinkedHashMap<>(capacity * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
                return size() > capacity;
            }
        };
        return key -> {
            if (map.get(key) != null) {
                return true;
            }
            map.put(key, Boolean.TRUE);
            return false;
        };
    }

    /** 近似 LRU：随机抽 samples 个，淘汰 lru 时间最早的 */
    static Cache sampledLru(int capacity, int samples, long seed) {
        Random random = new Random(seed);
        List<Integer> keys = new ArrayList<>(capacity);
        Map<Integer, Long> lruClock = new HashMap<>();
        long[] clock = {0};
        return key -> {
            clock[0]++;
            if (lruClock.containsKey(key)) {
                lruClock.put(key, clock[0]);
                return true;
            }
            if (keys.size() >= capacity) {
                int victimPos = -1;
                long oldest = Long.MAX_VALUE;
                for (int i = 0; i < samples; i++) {
                    int pos = random.nextInt(keys.size());
                    long t = lruClock.get(keys.get(pos));
                    if (t < oldest) {
                        oldest = t;
                        victimPos = pos;
                    }
                }
                int victim = keys.get(victimPos);
                int last = keys.remove(keys.size() - 1);
                if (victimPos < keys.size()) {
                    keys.set(victimPos, last);
                }
                lruClock.remove(victim);
            }
            keys.add(key);
            lruClock.put(key, clock[0]);
            return false;
        };
    }

    static double hitRate(Cache cache, int[] requests) {
        int hits = 0;
        for (int key : requests) {
            if (cache.access(key)) {
                hits++;
            }
        }
        return hits * 100.0 / requests.length;
    }

    public static void main(String[] args) {
        int keySpace = 10_000;
        int capacity = 1_000;
        int[] requests = new int[2_000_000];
        Random random = new Random(42);
        // 80% 的请求落在 20% 的热点 key 上
        for (int i = 0; i < requests.length; i++) {
            requests[i] = random.nextInt(100) < 80
                    ? random.nextInt(keySpace / 5)
                    : keySpace / 5 + random.nextInt(keySpace * 4 / 5);
        }

        System.out.printf("缓存容量 %d，key 总数 %d，80%% 请求访问 20%% 的 key%n", capacity, keySpace);
        System.out.printf("标准 LRU                 命中率 %.2f%%%n", hitRate(exactLru(capacity), requests));
        for (int samples : new int[]{1, 3, 5, 10}) {
            System.out.printf("近似 LRU（抽样 %2d 个）    命中率 %.2f%%%n",
                    samples, hitRate(sampledLru(capacity, samples, 7), requests));
        }
        // 预期：抽样 1 个就是随机淘汰，命中率最低；抽样 5 个已经和标准 LRU 很接近；抽样 10 个几乎一样
        // 对应配置：maxmemory-samples 5（默认），调大更准但更耗 CPU
    }
}
