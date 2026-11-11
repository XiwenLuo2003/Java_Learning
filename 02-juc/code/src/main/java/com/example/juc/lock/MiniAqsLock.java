package com.example.juc.lock;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 迷你版 ReentrantLock，保留 AQS（AbstractQueuedSynchronizer）的骨架：
 * <ul>
 *   <li><b>state</b>：AQS 里是 volatile int state，用 CAS 修改。0 表示没人持有，n 表示被同一个线程重入了 n 次</li>
 *   <li><b>owner</b>：AQS 父类 AbstractOwnableSynchronizer 的 exclusiveOwnerThread，当前持有锁的线程</li>
 *   <li><b>等待队列</b>：AQS 里是 CLH 变体的双向链表（head、tail、Node.prev/next/waitStatus），这里用 ConcurrentLinkedQueue 代替</li>
 *   <li><b>挂起 / 唤醒</b>：抢不到锁的线程 LockSupport.park 挂起；释放锁时 unpark 队头线程</li>
 * </ul>
 * ReentrantLock 的公平 / 非公平只差在 tryAcquire 里有没有 hasQueuedPredecessors() 这一个判断。
 */
public class MiniAqsLock {

    private final AtomicInteger state = new AtomicInteger(0);
    private volatile Thread owner;
    private final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();
    private final boolean fair;

    public MiniAqsLock(boolean fair) {
        this.fair = fair;
    }

    /** 对应 AQS#acquire：先 tryAcquire，失败就入队（addWaiter），在队列里循环“检查 → park”（acquireQueued） */
    public void lock() {
        if (tryAcquire()) {
            return;
        }
        Thread current = Thread.currentThread();
        waiters.add(current);
        while (true) {
            // 只有排在队头的线程才有资格去抢锁
            if (waiters.peek() == current && tryAcquire()) {
                waiters.poll();
                return;
            }
            // 挂起。被 unpark 或虚假唤醒后回到循环开头重新检查，所以虚假唤醒不会出错
            LockSupport.park(this);
        }
    }

    /** 对应 ReentrantLock.Sync 的 tryAcquire：非公平是 nonfairTryAcquire，公平是 FairSync#tryAcquire */
    private boolean tryAcquire() {
        Thread current = Thread.currentThread();
        int c = state.get();
        if (c == 0) {
            // 公平锁：队列里有人比我先来，我就不抢。非公平锁：不管队列，直接 CAS 抢（可能插队成功）
            if (fair && hasQueuedPredecessors(current)) {
                return false;
            }
            if (state.compareAndSet(0, 1)) {
                owner = current;
                return true;
            }
        } else if (owner == current) {
            // 重入：已经是自己持有，state + 1。只有持锁线程会走到这里，不需要 CAS
            state.set(c + 1);
            return true;
        }
        return false;
    }

    private boolean hasQueuedPredecessors(Thread current) {
        Thread head = waiters.peek();
        return head != null && head != current;
    }

    /** 对应 AQS#release：tryRelease 把 state 减到 0 才算真正释放，然后 unpark 队头（unparkSuccessor） */
    public void unlock() {
        if (owner != Thread.currentThread()) {
            throw new IllegalMonitorStateException("不是锁的持有者");
        }
        int c = state.get() - 1;
        if (c == 0) {
            owner = null;
            state.set(0);
            Thread head = waiters.peek();
            if (head != null) {
                // 就算 head 还没来得及 park，unpark 发出的“许可证”也会让它之后的 park 立刻返回，不会丢失唤醒
                LockSupport.unpark(head);
            }
        } else {
            state.set(c);
        }
    }

    public int getHoldCount() {
        return owner == Thread.currentThread() ? state.get() : 0;
    }

    // ===== 验证 =====

    private static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        for (boolean fair : new boolean[]{false, true}) {
            MiniAqsLock lock = new MiniAqsLock(fair);
            count = 0;
            Thread[] threads = new Thread[4];
            for (int t = 0; t < threads.length; t++) {
                threads[t] = new Thread(() -> {
                    for (int i = 0; i < 100_000; i++) {
                        lock.lock();
                        try {
                            count++;
                        } finally {
                            lock.unlock();
                        }
                    }
                });
                threads[t].start();
            }
            long start = System.currentTimeMillis();
            for (Thread thread : threads) {
                thread.join();
            }
            System.out.printf("%s锁：4 个线程各加 100000 次，结果 = %d，耗时约 %d ms%n",
                    fair ? "公平" : "非公平", count, System.currentTimeMillis() - start);
        }
        System.out.println("非公平锁通常更快：刚释放锁时，正在运行的线程直接 CAS 抢到，省掉了唤醒队头线程的上下文切换");

        MiniAqsLock lock = new MiniAqsLock(false);
        lock.lock();
        lock.lock();
        System.out.println("\n重入两次，holdCount = " + lock.getHoldCount());
        lock.unlock();
        lock.unlock();
        System.out.println("解锁两次，holdCount = " + lock.getHoldCount() + "；lock 几次就必须 unlock 几次");
    }
}
