package com.example.juc.print;

import java.util.concurrent.Semaphore;

/**
 * 双线程交替打印 1~100（Semaphore 版），代码最短、最不容易写错，面试时可以作为第二种解法。
 * <p>
 * 两个信号量当“接力棒”：odd 初始 1 个许可，奇数线程先跑；打印完把许可交给 even，自己再去等 odd。
 * 每个线程打印 50 次，循环次数固定，不需要额外的退出判断。
 */
public class AlternatePrintSemaphore {

    private static final Semaphore odd = new Semaphore(1);
    private static final Semaphore even = new Semaphore(0);

    public static void main(String[] args) throws InterruptedException {
        Thread oddThread = new Thread(() -> print(1, odd, even), "奇数线程");
        Thread evenThread = new Thread(() -> print(2, even, odd), "偶数线程");
        oddThread.start();
        evenThread.start();
        oddThread.join();
        evenThread.join();
    }

    private static void print(int start, Semaphore mine, Semaphore other) {
        for (int i = start; i <= 100; i += 2) {
            try {
                mine.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.println(Thread.currentThread().getName() + ": " + i);
            other.release();
        }
    }
}
