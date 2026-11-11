package com.example.juc.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.example.juc.Sleeper.sleep;

/**
 * ReentrantLock 比 synchronized 多出来的能力：tryLock 超时、可中断、公平锁。
 */
public class ReentrantLockDemo {

    public static void main(String[] args) throws InterruptedException {
        tryLockWithTimeout();
        lockInterruptibly();
        fairness();
    }

    /** 实验 1：tryLock(timeout)：等一会儿拿不到就放弃，不会像 synchronized 那样一直等，可以用来避免死锁 */
    static void tryLockWithTimeout() throws InterruptedException {
        System.out.println("== 实验 1：tryLock 超时 ==");
        ReentrantLock lock = new ReentrantLock();
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                sleep(1000);
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        sleep(100);
        long start = System.currentTimeMillis();
        boolean got = lock.tryLock(300, TimeUnit.MILLISECONDS);
        System.out.printf("等了 %d ms，拿到锁了吗？%s → 可以去做降级处理，比如返回“系统繁忙”%n",
                System.currentTimeMillis() - start, got);
        if (got) {
            lock.unlock();
        }
        holder.join();
    }

    /** 实验 2：lockInterruptibly：等锁的过程中可以被 interrupt 打断；synchronized 和 lock() 等锁时不响应中断 */
    static void lockInterruptibly() throws InterruptedException {
        System.out.println("\n== 实验 2：可中断 ==");
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        Thread waiter = new Thread(() -> {
            try {
                lock.lockInterruptibly();
                try {
                    System.out.println("拿到锁了（不应该走到这里）");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                System.out.println("waiter 在等锁时被中断，放弃等待，抛出 InterruptedException");
            }
        });
        waiter.start();
        sleep(100);
        waiter.interrupt();
        waiter.join();
        lock.unlock();
    }

    /**
     * 实验 3：公平 vs 非公平。5 个线程各抢 3 次锁，记录拿到锁的顺序。
     * 公平锁按排队顺序轮流（A B C D E A B C D E ...）；非公平锁经常是同一个线程连续拿到（刚释放又立刻抢回来）。
     */
    static void fairness() throws InterruptedException {
        System.out.println("\n== 实验 3：公平 vs 非公平 ==");
        for (boolean fair : new boolean[]{true, false}) {
            ReentrantLock lock = new ReentrantLock(fair);
            List<String> order = Collections.synchronizedList(new ArrayList<>());
            List<Thread> threads = new ArrayList<>();
            lock.lock();   // 先让所有线程都排上队
            for (char name = 'A'; name <= 'E'; name++) {
                String threadName = String.valueOf(name);
                Thread t = new Thread(() -> {
                    for (int i = 0; i < 3; i++) {
                        lock.lock();
                        try {
                            order.add(threadName);
                        } finally {
                            lock.unlock();
                        }
                    }
                }, threadName);
                threads.add(t);
                t.start();
                sleep(20);   // 保证按 A B C D E 的顺序入队
            }
            lock.unlock();
            for (Thread t : threads) {
                t.join();
            }
            System.out.println((fair ? "公平锁  " : "非公平锁") + "：" + String.join(" ", order));
        }
    }
}
