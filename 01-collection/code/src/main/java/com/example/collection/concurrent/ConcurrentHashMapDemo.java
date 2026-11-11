package com.example.collection.concurrent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

/**
 * HashMap 线程不安全 vs ConcurrentHashMap，以及 ConcurrentHashMap 的常见误用。
 */
public class ConcurrentHashMapDemo {

    static final int THREADS = 4;
    static final int PER_THREAD = 10_000;

    public static void main(String[] args) throws InterruptedException {
        // ===== 实验 1：HashMap 多线程 put 不同的 key，size 小于 40000（数据丢了） =====
        // 典型原因：两个线程同时发现同一个桶为空，各自 tab[i] = newNode，后写的覆盖先写的；++size 也不是原子操作
        System.out.println("== 实验 1：HashMap 并发 put ==");
        for (int round = 1; round <= 3; round++) {
            Map<Integer, Integer> hashMap = new HashMap<>();
            String error = runConcurrently(t -> {
                for (int i = 0; i < PER_THREAD; i++) {
                    int key = t * PER_THREAD + i;
                    hashMap.put(key, key);
                }
            });
            System.out.printf("第 %d 轮：预期 %d，实际 size = %d%s%n", round, THREADS * PER_THREAD, hashMap.size(),
                    error == null ? "" : "，线程内还抛了异常：" + error);
        }

        // ===== 实验 2：ConcurrentHashMap 和 MiniConcurrentHashMap 都是 40000 =====
        System.out.println("\n== 实验 2：ConcurrentHashMap 并发 put ==");
        Map<Integer, Integer> chm = new ConcurrentHashMap<>();
        runConcurrently(t -> {
            for (int i = 0; i < PER_THREAD; i++) {
                int key = t * PER_THREAD + i;
                chm.put(key, key);
            }
        });
        System.out.println("ConcurrentHashMap size = " + chm.size());

        // 容量故意只给 16，让大量 key 冲突，观察两条路径各走了多少次
        MiniConcurrentHashMap<Integer, Integer> mini = new MiniConcurrentHashMap<>(16);
        runConcurrently(t -> {
            for (int i = 0; i < PER_THREAD; i++) {
                int key = t * PER_THREAD + i;
                mini.put(key, key);
            }
        });
        System.out.printf("MiniConcurrentHashMap size = %d：CAS 放头节点 %d 次（失败 %d 次），加锁插入 %d 次%n",
                mini.size(), mini.casSuccess.get(), mini.casFailure.get(), mini.lockedPut.get());

        // ===== 实验 3：ConcurrentHashMap 只保证单个方法线程安全，“先 get 再 put”的复合操作照样会丢 =====
        System.out.println("\n== 实验 3：复合操作 ==");
        Map<String, Integer> wrong = new ConcurrentHashMap<>();
        wrong.put("pv", 0);
        runConcurrently(t -> {
            for (int i = 0; i < PER_THREAD; i++) {
                Integer old = wrong.get("pv");
                wrong.put("pv", old + 1);   // 两个线程读到同一个 old，各自 +1 写回，少加了一次
            }
        });
        Map<String, Integer> right = new ConcurrentHashMap<>();
        runConcurrently(t -> {
            for (int i = 0; i < PER_THREAD; i++) {
                right.merge("pv", 1, Integer::sum);   // merge / compute 在桶锁内完成“读-改-写”，是原子的
            }
        });
        System.out.println("get + put 结果 = " + wrong.get("pv") + "，merge 结果 = " + right.get("pv")
                + "，预期 " + THREADS * PER_THREAD);

        // ===== 实验 4：不允许 null =====
        System.out.println("\n== 实验 4：null ==");
        try {
            new ConcurrentHashMap<String, String>().put("k", null);
        } catch (NullPointerException e) {
            System.out.println("ConcurrentHashMap put null value：NullPointerException");
        }
        Map<String, String> hm = new HashMap<>();
        hm.put(null, null);
        System.out.println("HashMap 允许 null key 和 null value，size = " + hm.size());

        // ===== 实验 5：弱一致性迭代。遍历时修改不抛 ConcurrentModificationException =====
        System.out.println("\n== 实验 5：弱一致性迭代 ==");
        Map<Integer, Integer> iterating = new ConcurrentHashMap<>(Map.of(1, 1, 2, 2, 3, 3));
        for (Integer key : iterating.keySet()) {
            if (key < 100) {
                iterating.put(key + 100, 0);
            }
        }
        System.out.println("遍历中 put 没有报错，最终 size = " + iterating.size() + "（新加的 key 可能被遍历到，也可能没有）");
    }

    /** 开 THREADS 个线程执行同一段代码，返回线程里抛出的第一个异常（没有则返回 null） */
    static String runConcurrently(IntConsumer task) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        String[] error = new String[1];
        for (int t = 0; t < THREADS; t++) {
            int id = t;
            Thread thread = new Thread(() -> {
                try {
                    task.accept(id);
                } catch (RuntimeException e) {
                    error[0] = e.getClass().getSimpleName();
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        return error[0];
    }
}
