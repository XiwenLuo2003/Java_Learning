package com.example.jvm.gc;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

/**
 * 四种引用的回收时机。
 * <pre>
 * 强引用 Object o = new Object()   只要可达，宁可 OOM 也不回收
 * 软引用 SoftReference             内存不够（即将 OOM）时才回收，适合做内存敏感的缓存
 * 弱引用 WeakReference             只要发生 GC 就回收，ThreadLocalMap 的 key、WeakHashMap 用的就是它
 * 虚引用 PhantomReference          get() 永远返回 null，只用来在对象被回收时收到通知（配合 ReferenceQueue），
 *                                  DirectByteBuffer 释放堆外内存的 Cleaner 就基于它
 * </pre>
 * VM 参数：-Xmx20m -Xlog:gc
 */
public class ReferenceDemo {

    private static final int MB = 1024 * 1024;

    public static void main(String[] args) throws InterruptedException {
        // ===== 软引用 =====
        SoftReference<byte[]> soft = new SoftReference<>(new byte[8 * MB]);
        System.gc();
        System.out.println("软引用：内存充足时 GC 后还在吗？" + (soft.get() != null));
        try {
            byte[] big = new byte[12 * MB];   // 8 + 12 > 20MB，JVM 会在抛 OOM 之前先清掉软引用
            System.out.println("分配 12MB 成功（数组长度 " + big.length + "），软引用还在吗？" + (soft.get() != null));
        } catch (OutOfMemoryError e) {
            System.out.println("分配 12MB 仍然 OOM（堆太碎），软引用还在吗？" + (soft.get() != null));
        }

        // ===== 弱引用 =====
        WeakReference<byte[]> weak = new WeakReference<>(new byte[MB]);
        System.out.println("\n弱引用：GC 前还在吗？" + (weak.get() != null));
        System.gc();
        System.out.println("弱引用：GC 后还在吗？" + (weak.get() != null));

        // ===== 虚引用 =====
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> phantom = new PhantomReference<>(new Object(), queue);
        System.out.println("\n虚引用：get() = " + phantom.get() + "（永远是 null）");
        System.gc();
        Reference<?> polled = queue.remove(1000);   // 对象被回收后，虚引用本身会被放进队列
        System.out.println("虚引用：对象被回收后，从 ReferenceQueue 收到通知了吗？" + (polled == phantom));
    }
}
