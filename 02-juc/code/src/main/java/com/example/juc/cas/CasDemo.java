package com.example.juc.cas;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * CAS（Compare And Swap）：比较内存里的值是不是预期值，是就替换成新值，否则什么都不做，返回是否成功。
 * 整个“比较 + 替换”由一条 CPU 指令完成（x86 上是 lock cmpxchg），所以是原子的，不需要加锁。
 */
public class CasDemo {

    public static void main(String[] args) throws InterruptedException {
        atomicVsPlain();
        manualSpin();
        aba();
        longAdder();
    }

    /** 实验 1：AtomicInteger.incrementAndGet 是原子的 */
    static void atomicVsPlain() throws InterruptedException {
        System.out.println("== 实验 1：AtomicInteger ==");
        AtomicInteger atomic = new AtomicInteger();
        int[] plain = {0};
        runThreads(4, () -> {
            for (int i = 0; i < 100_000; i++) {
                atomic.incrementAndGet();
                plain[0]++;
            }
        });
        System.out.println("预期 400000，AtomicInteger = " + atomic.get() + "，普通 int = " + plain[0]);
    }

    /**
     * 实验 2：自己用 compareAndSet 写一个自增，和 JDK 的实现一模一样。
     * AtomicInteger#incrementAndGet → Unsafe#getAndAddInt：
     * <pre>
     * public final int getAndAddInt(Object o, long offset, int delta) {
     *     int v;
     *     do {
     *         v = getIntVolatile(o, offset);                       // 读当前值（value 字段是 volatile）
     *     } while (!weakCompareAndSetInt(o, offset, v, v + delta)); // CAS 失败说明被别人改了，重新读再试
     *     return v;
     * }
     * </pre>
     */
    static void manualSpin() throws InterruptedException {
        System.out.println("\n== 实验 2：手写 CAS 自旋 ==");
        AtomicInteger value = new AtomicInteger();
        AtomicInteger retries = new AtomicInteger();
        runThreads(4, () -> {
            for (int i = 0; i < 100_000; i++) {
                int v;
                do {
                    v = value.get();
                } while (!casOrCountRetry(value, v, v + 1, retries));
            }
        });
        System.out.println("结果 = " + value.get() + "，CAS 失败重试了约 " + retries.get()
                + " 次 —— 竞争越激烈重试越多，空转浪费 CPU，这是 CAS 的缺点之一");
    }

    private static boolean casOrCountRetry(AtomicInteger value, int expect, int update, AtomicInteger retries) {
        boolean ok = value.compareAndSet(expect, update);
        if (!ok) {
            retries.incrementAndGet();
        }
        return ok;
    }

    /**
     * 实验 3：ABA 问题。值从 A 改成 B 又改回 A，CAS 只比较值，发现“没变”，照样成功。
     * 对数值计数一般无所谓；但对“栈顶指针”这类引用，A 虽然还是 A，下面的结构可能已经变了。
     * 解决：AtomicStampedReference 带一个版本号（stamp），每次修改版本号 + 1，比较时值和版本号都要对上。
     */
    static void aba() {
        System.out.println("\n== 实验 3：ABA ==");
        AtomicReference<String> ref = new AtomicReference<>("A");
        String seen = ref.get();             // 线程 1 读到 A，准备改成 C
        ref.set("B");                        // 线程 2：A → B
        ref.set("A");                        // 线程 2：B → A
        System.out.println("AtomicReference：中间被改过，CAS 仍然成功 = " + ref.compareAndSet(seen, "C"));

        AtomicStampedReference<String> stamped = new AtomicStampedReference<>("A", 0);
        int seenStamp = stamped.getStamp();  // 线程 1 读到 A，版本 0
        String seenValue = stamped.getReference();
        stamped.compareAndSet("A", "B", 0, 1);   // 线程 2：A → B，版本 1
        stamped.compareAndSet("B", "A", 1, 2);   // 线程 2：B → A，版本 2
        boolean ok = stamped.compareAndSet(seenValue, "C", seenStamp, seenStamp + 1);
        System.out.println("AtomicStampedReference：版本号 " + seenStamp + " ≠ " + stamped.getStamp()
                + "，CAS 失败 = " + !ok);
    }

    /**
     * 实验 4：LongAdder。AtomicLong 所有线程 CAS 同一个变量，竞争激烈时大量失败重试；
     * LongAdder 把计数分散到多个 Cell 上（每个线程按 hash 落到不同的 Cell），sum() 时再累加，用空间换时间。
     * ConcurrentHashMap 的 size 计数（baseCount + CounterCell[]）用的是同一个设计。
     */
    static void longAdder() throws InterruptedException {
        System.out.println("\n== 实验 4：AtomicLong vs LongAdder（8 线程各加 500 万次） ==");
        AtomicLong atomicLong = new AtomicLong();
        long start = System.currentTimeMillis();
        runThreads(8, () -> {
            for (int i = 0; i < 5_000_000; i++) {
                atomicLong.incrementAndGet();
            }
        });
        long atomicCost = System.currentTimeMillis() - start;

        LongAdder adder = new LongAdder();
        start = System.currentTimeMillis();
        runThreads(8, () -> {
            for (int i = 0; i < 5_000_000; i++) {
                adder.increment();
            }
        });
        long adderCost = System.currentTimeMillis() - start;
        System.out.printf("AtomicLong = %d，耗时 %d ms；LongAdder = %d，耗时 %d ms%n",
                atomicLong.get(), atomicCost, adder.sum(), adderCost);
        System.out.println("LongAdder 的 sum() 不是强一致的快照（累加时别的线程还在加），适合统计类场景，不适合做序号生成");
    }

    static void runThreads(int n, Runnable task) throws InterruptedException {
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }
}
