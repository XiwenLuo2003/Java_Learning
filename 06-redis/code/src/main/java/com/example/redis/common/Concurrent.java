package com.example.redis.common;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * 实验用的并发工具：所有任务先在起跑线（CountDownLatch）上等着，再同时放行，尽量让请求真正“同时”到达。
 */
public final class Concurrent {

    private Concurrent() {
    }

    public static <T> List<T> run(int tasks, IntFunction<T> task) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks, 200));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(tasks);
        for (int i = 0; i < tasks; i++) {
            int index = i;
            futures.add(pool.submit(() -> {
                start.await();
                return task.apply(index);
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>(tasks);
        try {
            for (Future<T> f : futures) {
                results.add(f.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } finally {
            pool.shutdown();
        }
        return results;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
