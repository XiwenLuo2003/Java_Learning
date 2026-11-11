package com.example.juc.deadlock;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import static com.example.juc.Sleeper.sleep;

/**
 * 死锁：线程 A 拿着锁 1 等锁 2，线程 B 拿着锁 2 等锁 1。
 * <p>
 * 四个必要条件：互斥、持有并等待、不可剥夺、循环等待。破坏任意一个就不会死锁，最常用的是：
 * <ul>
 *   <li>破坏循环等待：所有线程按同一个顺序加锁（比如按账户 id 从小到大）</li>
 *   <li>破坏不可剥夺：用 ReentrantLock.tryLock(timeout)，拿不到就释放已持有的锁，过会儿重试</li>
 * </ul>
 * 程序启动后会一直卡住（线程是非守护线程），可以用来练习排查：
 * <pre>
 * jps -l                      找到 DeadlockDemo 的 pid
 * jstack &lt;pid&gt;                末尾会打印 "Found one Java-level deadlock"，列出谁持有哪把锁、在等哪把锁
 * </pre>
 * 看完后在 IDEA 里点停止按钮结束程序。
 */
public class DeadlockDemo {

    private static final Object LOCK_1 = new Object();
    private static final Object LOCK_2 = new Object();

    public static void main(String[] args) {
        new Thread(() -> {
            synchronized (LOCK_1) {
                sleep(100);   // 保证对方已经拿到 LOCK_2
                synchronized (LOCK_2) {
                    System.out.println("A 拿到了两把锁");
                }
            }
        }, "thread-A").start();

        new Thread(() -> {
            synchronized (LOCK_2) {
                sleep(100);
                synchronized (LOCK_1) {
                    System.out.println("B 拿到了两把锁");
                }
            }
        }, "thread-B").start();

        // 程序内部也能检测死锁：ThreadMXBean 就是 jstack 背后用的那套接口
        sleep(500);
        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        long[] ids = mxBean.findDeadlockedThreads();
        if (ids != null) {
            System.out.println("检测到死锁：");
            for (ThreadInfo info : mxBean.getThreadInfo(ids)) {
                System.out.printf("  %s 状态 %s，等待 %s，这把锁被 %s 持有%n",
                        info.getThreadName(), info.getThreadState(), info.getLockName(), info.getLockOwnerName());
            }
            System.out.println("程序会一直卡在这里。现在打开终端执行 jps -l 和 jstack <pid> 看看，看完点 IDEA 的停止按钮。");
        }
    }
}
