package com.example.juc.sync;

import static com.example.juc.Sleeper.sleep;

/**
 * synchronized 基础：竞态条件、对象锁 vs 类锁、可重入。
 * <p>
 * 字节码层面（编译后在 target/classes 下执行 javap -c -v SyncCounterDemo.class 查看）：
 * <ul>
 *   <li>同步代码块：monitorenter + 两个 monitorexit（第二个在异常处理路径上，保证抛异常也能释放锁）</li>
 *   <li>同步方法：没有 monitorenter，方法的 flags 里多了 ACC_SYNCHRONIZED，JVM 调用方法时自动加锁</li>
 * </ul>
 * 两者底层都是获取对象关联的 Monitor（HotSpot 里是 C++ 的 ObjectMonitor：_owner 持有者、_recursions 重入次数、
 * _EntryList 抢锁失败的线程、_WaitSet 调用了 wait 的线程）。
 */
public class SyncCounterDemo {

    private static final int THREADS = 4;
    private static final int TIMES = 100_000;

    private static int unsafeCount = 0;
    private static int safeCount = 0;
    private static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        // ===== 实验 1：count++ 不是原子操作 =====
        // 它是三条字节码：getstatic（读）→ iadd（加 1）→ putstatic（写）。两个线程同时读到 5，各自写回 6，少加了一次
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < TIMES; i++) {
                    unsafeCount++;
                    synchronized (LOCK) {
                        safeCount++;
                    }
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("== 实验 1：竞态条件 ==");
        System.out.printf("预期 %d，不加锁 = %d，加锁 = %d%n", THREADS * TIMES, unsafeCount, safeCount);

        // ===== 实验 2：锁的是谁？=====
        // 实例同步方法锁 this，静态同步方法锁 SyncCounterDemo.class，是两把不同的锁，互不阻塞
        System.out.println("\n== 实验 2：对象锁 vs 类锁 ==");
        SyncCounterDemo demo = new SyncCounterDemo();
        long start = System.currentTimeMillis();
        Thread a = new Thread(demo::instanceMethod);
        Thread b = new Thread(SyncCounterDemo::staticMethod);
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("两个方法各 sleep 500ms，总耗时约 " + (System.currentTimeMillis() - start)
                + " ms（约 500 说明没有互斥，约 1000 才是互斥）");

        // ===== 实验 3：可重入。同一个线程拿到锁后再次进入同一把锁的代码不会死锁 =====
        System.out.println("\n== 实验 3：可重入 ==");
        demo.outer();
    }

    synchronized void instanceMethod() {
        sleep(500);
    }

    static synchronized void staticMethod() {
        sleep(500);
    }

    synchronized void outer() {
        System.out.println("进入 outer，持有 this 锁");
        inner();   // 再次申请 this 锁：Monitor 发现 owner 是自己，重入计数 +1，直接进入
    }

    synchronized void inner() {
        System.out.println("进入 inner，没有死锁 —— synchronized 是可重入锁");
    }
}
