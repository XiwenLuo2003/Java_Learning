package com.example.juc.sync;

import static com.example.juc.Sleeper.sleep;

/**
 * volatile 的两个作用：可见性、禁止指令重排序；以及它不保证原子性。
 */
public class VolatileDemo {

    private static boolean plainStop = false;
    private static volatile boolean volatileStop = false;
    private static volatile int volatileCount = 0;

    public static void main(String[] args) throws InterruptedException {
        // ===== 实验 1：可见性 =====
        // 普通变量：JIT 编译热点循环时发现循环体里没人改 plainStop，可能把它提到循环外只读一次，
        // 变成 if (!plainStop) while (true)，于是主线程改了也看不到（大概率复现，取决于 JIT）。
        // 注意循环体里不能写 System.out.println：println 内部有 synchronized，会顺带刷新变量，掩盖问题。
        Thread plain = new Thread(() -> {
            while (!plainStop) {
                // 空循环
            }
        }, "plain");
        plain.setDaemon(true);   // 守护线程：就算停不下来，main 结束时 JVM 也能退出
        plain.start();

        Thread withVolatile = new Thread(() -> {
            while (!volatileStop) {
                // 空循环
            }
        }, "volatile");
        withVolatile.setDaemon(true);
        withVolatile.start();

        sleep(1000);   // 给 JIT 足够时间编译这两个循环
        plainStop = true;
        volatileStop = true;
        plain.join(1000);
        withVolatile.join(1000);
        System.out.println("== 实验 1：可见性 ==");
        System.out.println("普通变量的线程停下了吗？" + !plain.isAlive());
        System.out.println("volatile 变量的线程停下了吗？" + !withVolatile.isAlive());

        // ===== 实验 2：volatile 不保证原子性 =====
        Thread[] threads = new Thread[4];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 100_000; i++) {
                    volatileCount++;   // 读-改-写三步，每一步都可见，但三步之间会被别的线程插进来
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        System.out.println("\n== 实验 2：volatile 不保证原子性 ==");
        System.out.println("预期 400000，实际 " + volatileCount + "；要原子性用 synchronized 或 AtomicInteger");

        System.out.println("\n实验 3：双重检查锁单例见 Singleton 类的注释（重排序很难稳定复现，只讲原理）");
    }

    /**
     * 双重检查锁（DCL）单例：instance 必须加 volatile。
     * <p>
     * instance = new Singleton() 分三步：① 分配内存 ② 执行构造器初始化 ③ 把引用赋给 instance。
     * 不加 volatile 时 ②③ 可能被重排序为 ①③②：线程 A 执行完 ③ 还没执行 ②，
     * 线程 B 在第一次检查时看到 instance != null，直接返回了一个还没初始化完的对象。
     * volatile 写之前会插入 StoreStore 屏障，保证构造器里的写入先于 instance 的赋值对其他线程可见。
     */
    static final class Singleton {
        private static volatile Singleton instance;

        private Singleton() {
        }

        static Singleton getInstance() {
            if (instance == null) {                    // 第一次检查：已创建就不加锁，提高性能
                synchronized (Singleton.class) {
                    if (instance == null) {            // 第二次检查：防止两个线程都通过了第一次检查，重复创建
                        instance = new Singleton();
                    }
                }
            }
            return instance;
        }
    }
}
