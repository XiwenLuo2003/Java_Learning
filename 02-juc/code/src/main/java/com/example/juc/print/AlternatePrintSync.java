package com.example.juc.print;

/**
 * 手撕题：双线程交替打印 1~100（synchronized + wait/notifyAll 版）。
 * 代码来自学习路线 java.md 的“必背题 1”，原样保留，只补充了注释里的易错点。
 * <p>
 * 三个易错点：
 * <ol>
 *   <li>wait / notify 必须在 synchronized 块里调用，并且是对同一个锁对象调用，否则抛 IllegalMonitorStateException</li>
 *   <li>判断“轮没轮到自己”要放在循环里（这里是外层 while(true)），被唤醒后要重新判断，防止虚假唤醒</li>
 *   <li>结束时要先 notifyAll 再 return，否则对方线程永远 wait 在那里，程序无法退出</li>
 * </ol>
 */
public class AlternatePrintSync {
    // 1. 定义一把共享锁对象和当前要打印的数字
    private static final Object lock = new Object();
    private static int current = 1;

    public static void main(String[] args) throws InterruptedException {
        // 线程1负责打印奇数 (isOdd = true)，线程2负责打印偶数 (isOdd = false)
        Thread oddThread = new Thread(() -> printNumber(true), "奇数线程");
        Thread evenThread = new Thread(() -> printNumber(false), "偶数线程");

        oddThread.start();
        evenThread.start();

        oddThread.join();
        evenThread.join();
    }

    private static void printNumber(boolean isOdd) {
        while (true) {
            synchronized (lock) {
                // 终止条件：超过100时，必须先唤醒对方再退出，防止对方永久 wait 挂死！
                if (current > 100) {
                    lock.notifyAll();
                    return;
                }

                // 判断当前数字是否轮到自己打印
                if ((current % 2 == 1) == isOdd) {
                    System.out.println(Thread.currentThread().getName() + ": " + current);
                    current++;
                    // 打印完唤醒另一个等待的线程
                    lock.notifyAll();
                } else {
                    // 没轮到自己，释放锁并进入等待状态
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
