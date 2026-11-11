package com.example.jvm.troubleshoot;

import java.util.ArrayList;
import java.util.List;

/**
 * 排查练习靶子：同时制造 CPU 飙高、内存泄漏、死锁三种问题，用 JDK 自带命令把它们一一找出来。
 * 详细步骤见 README.md 的“线上排查实操”。
 * <p>
 * VM 参数：
 * <pre>-Xms256m -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=troubleshoot.hprof</pre>
 * Program arguments（可选）：cpu / leak / deadlock，只开一种问题；不填三种都开。
 * 内存泄漏每秒涨 2MB，大约 2 分钟后 OOM；练完记得点 IDEA 的停止按钮。
 */
public class TroubleshootTarget {

    /** 只加不删的“缓存”，内存泄漏的元凶 */
    static final List<byte[]> LEAKY_CACHE = new ArrayList<>();

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "all";
        if (mode.equals("all") || mode.equals("cpu")) {
            startCpuHog();
        }
        if (mode.equals("all") || mode.equals("leak")) {
            startLeak();
        }
        if (mode.equals("all") || mode.equals("deadlock")) {
            startDeadlock();
        }
        System.out.println("已启动：" + mode + "。pid = " + ProcessHandle.current().pid()
                + "，现在打开终端按 README 的步骤排查。");
    }

    /** CPU 飙高：一个线程死循环做计算 */
    static void startCpuHog() {
        new Thread(() -> {
            double x = 0;
            while (true) {
                x += Math.sqrt(x + 1);   // 业务里常见的原因：死循环、正则灾难性回溯、大量序列化
                if (x > 1e15) {
                    x = 0;
                }
            }
        }, "hot-loop").start();
    }

    /** 内存泄漏：每 500ms 往静态集合里放 1MB，永远不删 */
    static void startLeak() {
        new Thread(() -> {
            while (true) {
                LEAKY_CACHE.add(new byte[1024 * 1024]);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "leaky-cache").start();
    }

    /** 死锁：两个线程以相反顺序拿两把锁 */
    static void startDeadlock() {
        Object lockA = new Object();
        Object lockB = new Object();
        new Thread(() -> lockInOrder(lockA, lockB), "order-service").start();
        new Thread(() -> lockInOrder(lockB, lockA), "stock-service").start();
    }

    private static void lockInOrder(Object first, Object second) {
        synchronized (first) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (second) {
                System.out.println("不会打印到这里");
            }
        }
    }
}
