package com.example.juc.print;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 变形题：三个线程按 A → B → C 的顺序循环打印 10 轮（ReentrantLock + 多个 Condition 版）。
 * <p>
 * 和 synchronized 版的区别：synchronized 只有一个等待队列，notifyAll 会把所有线程都叫醒，
 * 没轮到的再去 wait，白白竞争一次锁；ReentrantLock 可以给每个线程建一个 Condition，
 * 打印完只 signal 下一个线程，实现“精准唤醒”。
 */
public class AlternatePrintCondition {

    private static final int ROUNDS = 10;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition[] conditions = {lock.newCondition(), lock.newCondition(), lock.newCondition()};
    /** 轮到第几个线程：0 = A，1 = B，2 = C */
    private int turn = 0;

    void print(int index, String letter) {
        for (int round = 0; round < ROUNDS; round++) {
            lock.lock();
            try {
                // while 而不是 if：防止虚假唤醒
                while (turn != index) {
                    conditions[index].await();   // 和 wait 一样会释放锁
                }
                System.out.print(letter);
                if (index == 2) {
                    System.out.println();
                }
                turn = (turn + 1) % 3;
                conditions[turn].signal();       // 只唤醒下一个线程
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();                   // 必须放在 finally 里
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AlternatePrintCondition printer = new AlternatePrintCondition();
        Thread a = new Thread(() -> printer.print(0, "A"));
        Thread b = new Thread(() -> printer.print(1, "B"));
        Thread c = new Thread(() -> printer.print(2, "C"));
        c.start();
        b.start();
        a.start();   // 启动顺序打乱也没关系，turn 决定谁先打印
        a.join();
        b.join();
        c.join();
    }
}
