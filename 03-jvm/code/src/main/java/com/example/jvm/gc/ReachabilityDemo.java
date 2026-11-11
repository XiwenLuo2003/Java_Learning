package com.example.jvm.gc;

import java.lang.ref.WeakReference;

/**
 * 判断对象是否存活：Java 用的是可达性分析，不是引用计数。
 * <p>
 * 引用计数的致命问题是循环引用：a 引用 b、b 引用 a，外部都不再引用它们，但计数都是 1，永远回收不掉。
 * 可达性分析从 GC Roots 出发沿引用链遍历，走不到的对象就是垃圾，循环引用的两个对象一起被回收。
 * <p>
 * GC Roots 包括：虚拟机栈中局部变量引用的对象、方法区中类静态变量引用的对象、常量引用的对象、
 * 本地方法栈中 JNI 引用的对象、被 synchronized 持有的对象、JVM 内部引用（系统类加载器、基本类型的 Class 对象等）。
 * <p>
 * VM 参数（可选，看 GC 日志）：-Xlog:gc
 */
public class ReachabilityDemo {

    static class Node {
        Node other;
        final byte[] payload = new byte[2 * 1024 * 1024];
    }

    /** 静态变量是 GC Root */
    private static Node staticRoot;

    public static void main(String[] args) throws InterruptedException {
        // 实验 1：循环引用，但都不可达 → 被回收
        Node a = new Node();
        Node b = new Node();
        a.other = b;
        b.other = a;
        WeakReference<Node> watchA = new WeakReference<>(a);   // 弱引用不影响回收，只用来“观察”对象还在不在
        a = null;
        b = null;
        System.gc();
        Thread.sleep(100);
        System.out.println("实验 1：a ⇄ b 循环引用、外部无引用，GC 后还在吗？" + (watchA.get() != null));

        // 实验 2：被静态变量（GC Root）引用 → 不会被回收
        staticRoot = new Node();
        staticRoot.other = new Node();
        WeakReference<Node> watchChild = new WeakReference<>(staticRoot.other);
        System.gc();
        Thread.sleep(100);
        System.out.println("实验 2：从静态变量出发能走到的对象，GC 后还在吗？" + (watchChild.get() != null));

        // 实验 3：断开引用链 → 可以被回收
        staticRoot.other = null;
        System.gc();
        Thread.sleep(100);
        System.out.println("实验 3：断开 staticRoot.other 之后，GC 后还在吗？" + (watchChild.get() != null));
    }
}
