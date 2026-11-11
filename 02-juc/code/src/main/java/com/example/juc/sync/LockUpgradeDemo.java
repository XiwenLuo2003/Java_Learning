package com.example.juc.sync;

import org.openjdk.jol.info.ClassLayout;

import static com.example.juc.Sleeper.sleep;

/**
 * 用 JOL 打印对象头（Mark Word），观察 synchronized 的锁状态变化。
 * <p>
 * 64 位 JVM 的 Mark Word 最低 2 位是锁标志：
 * <pre>
 * 01  无锁（还会存 hashCode、GC 分代年龄）     JOL 显示 non-biasable
 * 00  轻量级锁：指向持锁线程栈帧里 Lock Record 的指针    JOL 显示 thin lock
 * 10  重量级锁：指向 ObjectMonitor 的指针              JOL 显示 fat lock
 * 11  GC 标记
 * （101 偏向锁：JDK 15 起默认关闭，JDK 18 彻底移除，所以 JDK 17 上看不到）
 * </pre>
 * 运行时 JOL 可能提示要加 -Djdk.attach.allowAttachSelf 之类的参数，那只是警告，不影响结果。
 */
public class LockUpgradeDemo {

    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();
        print("1. 刚创建，无锁", lock);

        synchronized (lock) {
            // 没有竞争：CAS 把 Mark Word 换成指向 Lock Record 的指针，不需要操作系统介入
            print("2. 单线程加锁，轻量级锁", lock);
        }
        print("3. 释放后，回到无锁", lock);

        // 制造竞争：t 拿着锁 sleep，main 来抢，CAS 自旋失败后膨胀为重量级锁，main 被挂起在 ObjectMonitor 的 EntryList 上
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                sleep(500);
            }
        });
        holder.start();
        sleep(100);
        synchronized (lock) {
            print("4. 发生竞争后，重量级锁", lock);
        }
        holder.join();

        // 调用 wait 必须有 WaitSet，只有 ObjectMonitor 才有，所以会直接膨胀为重量级锁
        Object waitLock = new Object();
        synchronized (waitLock) {
            waitLock.wait(10);
            print("5. 调用过 wait，重量级锁", waitLock);
        }

        // hashCode 会写进无锁状态的 Mark Word（这也是 JDK 15 之前“计算过 hashCode 的对象不能再偏向”的原因）
        Object hashed = new Object();
        System.out.printf("%n6. 调用 hashCode() = 0x%x 之后，看 Mark Word 里出现了这个 hash：%n", hashed.hashCode());
        System.out.println(ClassLayout.parseInstance(hashed).toPrintable());
    }

    private static void print(String title, Object o) {
        System.out.println("\n" + title);
        System.out.println(ClassLayout.parseInstance(o).toPrintable());
    }
}
