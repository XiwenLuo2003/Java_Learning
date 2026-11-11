package com.example.juc.state;

import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

import static com.example.juc.Sleeper.sleep;

/**
 * 线程的 6 种状态（java.lang.Thread.State）逐个复现，用 getState() 打印出来。
 * <pre>
 * NEW ──start()──▶ RUNNABLE ──run 结束──▶ TERMINATED
 *                   │  ▲
 *   抢 synchronized 失败 │  │ 抢到锁
 *                   ▼  │
 *                 BLOCKED
 *
 * RUNNABLE ──wait() / join() / LockSupport.park()──▶ WAITING ──notify / 目标线程结束 / unpark──▶ RUNNABLE
 * RUNNABLE ──sleep(ms) / wait(ms) / join(ms) / parkNanos──▶ TIMED_WAITING ──超时或被唤醒──▶ RUNNABLE
 * </pre>
 * 注意 Java 没有“RUNNING”状态：操作系统层面的“就绪”和“运行中”都算 RUNNABLE，阻塞在 IO 上也是 RUNNABLE。
 */
public class ThreadStateDemo {

    private static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        // 1. NEW：创建了还没 start
        Thread t1 = new Thread(() -> { }, "t1");
        print("new 之后", t1);

        // 2. RUNNABLE：正在执行（这里是空转）
        Thread busy = new Thread(() -> {
            long end = System.currentTimeMillis() + 300;
            while (System.currentTimeMillis() < end) {
                // 空转，占着 CPU
            }
        }, "busy");
        busy.start();
        sleep(50);
        print("start 之后在运行", busy);

        // 3. TERMINATED：run 方法执行完
        busy.join();
        print("run 结束之后", busy);

        // 4. BLOCKED：等待进入 synchronized，锁被 main 线程拿着
        Thread blocked = new Thread(() -> {
            synchronized (LOCK) {
                // 拿到锁就结束
            }
        }, "blocked");
        synchronized (LOCK) {
            blocked.start();
            sleep(50);
            print("抢 synchronized 失败", blocked);
        }
        blocked.join();

        // 5. WAITING：Object.wait() 无限期等待，需要别人 notify
        Thread waiting = new Thread(() -> {
            synchronized (LOCK) {
                try {
                    LOCK.wait();   // wait 会释放锁，所以下面 main 线程才能进 synchronized 去 notify
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "waiting");
        waiting.start();
        sleep(50);
        print("调用 LOCK.wait()", waiting);
        synchronized (LOCK) {
            LOCK.notify();
        }
        waiting.join();

        // 6. TIMED_WAITING：sleep(ms)
        Thread sleeping = new Thread(() -> sleep(300), "sleeping");
        sleeping.start();
        sleep(50);
        print("调用 Thread.sleep(300)", sleeping);
        sleeping.join();

        // 7. 抢 ReentrantLock 失败是 WAITING，不是 BLOCKED！因为 AQS 底层用 LockSupport.park 挂起线程
        ReentrantLock reentrantLock = new ReentrantLock();
        Thread parked = new Thread(reentrantLock::lock, "parked");
        reentrantLock.lock();
        try {
            parked.start();
            sleep(50);
            print("抢 ReentrantLock 失败", parked);
        } finally {
            reentrantLock.unlock();
        }
        parked.join();

        // 8. join：main 等待 t 结束，main 处于 WAITING
        Thread mainThread = Thread.currentThread();
        Thread joiner = new Thread(() -> {
            sleep(50);
            print("main 调用 t.join() 时 main 的状态", mainThread);
        }, "joiner");
        joiner.start();
        joiner.join();

        // 9. park / unpark：先 unpark 再 park 不会阻塞（unpark 发的是一张“许可证”，最多存 1 张）
        Thread permit = new Thread(() -> {
            sleep(100);              // 保证 main 的 unpark 先执行
            LockSupport.park();      // 已经有许可证，直接返回
            System.out.println("    permit 线程：先 unpark 后 park，park 立刻返回，没有阻塞");
        }, "permit");
        permit.start();
        LockSupport.unpark(permit);
        permit.join();

        // 10. 重复 start 抛 IllegalThreadStateException：状态只能单向流转，TERMINATED 回不到 NEW
        try {
            t1.start();
            t1.join();
            t1.start();
        } catch (IllegalThreadStateException e) {
            System.out.println("同一个线程 start 两次：IllegalThreadStateException");
        }
    }

    private static void print(String scene, Thread thread) {
        System.out.printf("%-34s %-8s → %s%n", scene, thread.getName(), thread.getState());
    }
}
