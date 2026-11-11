package com.example.juc.tool;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.juc.Sleeper.sleep;

/**
 * 三个常用同步工具，底层都基于 AQS 的 state（CyclicBarrier 基于 ReentrantLock + Condition）。
 */
public class SyncToolsDemo {

    public static void main(String[] args) throws Exception {
        countDownLatch();
        semaphore();
        cyclicBarrier();
    }

    /**
     * 实验 1：CountDownLatch —— 一个线程等 N 个线程都完成。
     * 场景：接口要并行调 3 个下游服务，全部返回后再汇总。
     * 原理：state 初始为 N；countDown 让 state - 1；await 在 state 变成 0 之前一直 park。一次性的，不能重置。
     */
    static void countDownLatch() throws InterruptedException {
        System.out.println("== 实验 1：CountDownLatch ==");
        String[] services = {"用户服务", "订单服务", "库存服务"};
        CountDownLatch latch = new CountDownLatch(services.length);
        long start = System.currentTimeMillis();
        for (int i = 0; i < services.length; i++) {
            String service = services[i];
            int cost = (i + 1) * 200;
            new Thread(() -> {
                try {
                    sleep(cost);
                    System.out.println("    " + service + " 返回，耗时 " + cost + " ms");
                } finally {
                    latch.countDown();   // 放 finally 里：任务抛异常也要 countDown，否则主线程永远等下去
                }
            }).start();
        }
        boolean allDone = latch.await(2, TimeUnit.SECONDS);   // 带超时，防止某个下游卡死
        System.out.printf("全部返回 = %s，总耗时约 %d ms（并行，约等于最慢的那个，而不是 200+400+600）%n",
                allDone, System.currentTimeMillis() - start);
    }

    /**
     * 实验 2：Semaphore —— 限制同时访问某个资源的线程数（限流）。
     * 场景：下游接口最多承受 2 个并发。
     * 原理：state 初始为许可数；acquire 让 state - 1，不够就 park；release 让 state + 1 并唤醒等待者。
     */
    static void semaphore() throws InterruptedException {
        System.out.println("\n== 实验 2：Semaphore（6 个线程，2 个许可） ==");
        Semaphore permits = new Semaphore(2);
        AtomicInteger concurrent = new AtomicInteger();
        Thread[] threads = new Thread[6];
        for (int i = 0; i < threads.length; i++) {
            int id = i + 1;
            threads[i] = new Thread(() -> {
                try {
                    permits.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    System.out.println("    线程 " + id + " 进入，当前并发 = " + concurrent.incrementAndGet());
                    sleep(300);
                    concurrent.decrementAndGet();
                } finally {
                    permits.release();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("并发数从没超过 2");
    }

    /**
     * 实验 3：CyclicBarrier —— N 个线程互相等，全部到齐后一起继续；可以重复使用（Cyclic）。
     * 和 CountDownLatch 的区别：Latch 是“一个等多个”、一次性；Barrier 是“多个互相等”、可循环。
     */
    static void cyclicBarrier() throws InterruptedException {
        System.out.println("\n== 实验 3：CyclicBarrier（3 个线程，跑 2 轮） ==");
        CyclicBarrier barrier = new CyclicBarrier(3, () -> System.out.println("    —— 3 个线程都到齐了，开始下一阶段 ——"));
        Thread[] threads = new Thread[3];
        for (int i = 0; i < threads.length; i++) {
            int id = i + 1;
            threads[i] = new Thread(() -> {
                try {
                    for (int round = 1; round <= 2; round++) {
                        sleep(id * 100L);
                        System.out.println("    线程 " + id + " 完成第 " + round + " 轮，等待其他线程");
                        barrier.await();
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}
