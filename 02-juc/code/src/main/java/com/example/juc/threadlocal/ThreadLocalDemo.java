package com.example.juc.threadlocal;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ThreadLocal：线程池里的串号问题、内存泄漏原理、父子线程传值。
 * <p>
 * 数据结构（JDK 源码）：值不是存在 ThreadLocal 里，而是存在每个 Thread 对象自己的 threadLocals 字段里：
 * <pre>
 * Thread
 *  └─ threadLocals: ThreadLocal.ThreadLocalMap
 *       └─ table: Entry[]
 *            └─ Entry extends WeakReference&lt;ThreadLocal&lt;?&gt;&gt;   ← key（ThreadLocal 对象）是弱引用
 *                 └─ Object value                              ← value 是强引用
 * </pre>
 * threadLocal.set(v) 等于 Thread.currentThread().threadLocals.set(this, v)。
 * <p>
 * VM 参数（实验 2 要反射读 Thread 的私有字段）：--add-opens java.base/java.lang=ALL-UNNAMED
 */
public class ThreadLocalDemo {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        crossTalk();
        leak();
        inheritable();
    }

    /** 实验 1：线程池的线程会被复用，上一个任务 set 的值没有 remove，下一个任务就读到了（串号） */
    static void crossTalk() throws InterruptedException {
        System.out.println("== 实验 1：线程池中的串号 ==");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> {
            CURRENT_USER.set("张三");
            System.out.println("请求 1（张三登录）：set 张三，忘了 remove");
        });
        pool.execute(() -> System.out.println("请求 2（没登录）：读到的当前用户 = " + CURRENT_USER.get() + "  ← 串号了！"));
        pool.execute(() -> {
            try {
                CURRENT_USER.set("李四");
                System.out.println("请求 3（李四登录）：set 李四，用完在 finally 里 remove");
            } finally {
                CURRENT_USER.remove();
            }
        });
        pool.execute(() -> System.out.println("请求 4（没登录）：读到的当前用户 = " + CURRENT_USER.get()));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    /** 一个“大对象”，方便在 ThreadLocalMap 里认出它 */
    static final class BigValue {
        final byte[] data = new byte[10 * 1024 * 1024];
    }

    /**
     * 实验 2：内存泄漏。ThreadLocal 对象没有强引用后被 GC 回收，Entry 的 key 变成 null，
     * 但 value 还被 Thread → ThreadLocalMap → Entry → value 这条强引用链拉着，回收不掉。
     * 线程池里的线程一直活着，这 10MB 就一直泄漏。
     */
    static void leak() throws Exception {
        System.out.println("\n== 实验 2：key 被回收，value 还在 ==");
        ThreadLocal<BigValue> local = new ThreadLocal<>();
        local.set(new BigValue());
        printOurEntry("set 之后");

        local = null;     // 业务代码不再引用这个 ThreadLocal（比如它是某个请求里 new 出来的局部变量）
        System.gc();      // key 是弱引用，GC 时会被回收
        Thread.sleep(200);
        printOurEntry("ThreadLocal 置 null 并 GC 之后");
        System.out.println("key 已经是 null，再也没法通过 ThreadLocal 访问到这个 value，但 10MB 还占着。");
        System.out.println("ThreadLocalMap 在之后的 set / get / remove 中遇到 key 为 null 的 Entry 会顺手清理（expungeStaleEntry），");
        System.out.println("但这是“碰巧”清理，不能指望。正确做法：用完在 finally 里调用 remove()。");
    }

    /** 反射遍历当前线程的 ThreadLocalMap，找到 value 是 BigValue 的 Entry */
    private static void printOurEntry(String title) throws Exception {
        Object map = field(Thread.currentThread(), Thread.class, "threadLocals");
        Object[] table = (Object[]) field(map, map.getClass(), "table");
        for (Object entry : table) {
            if (entry == null) {
                continue;
            }
            Object value = field(entry, entry.getClass(), "value");
            if (value instanceof BigValue) {
                Object key = ((WeakReference<?>) entry).get();
                System.out.printf("%s：Entry.key = %s，Entry.value = %s%n",
                        title, key == null ? "null" : "ThreadLocal@" + Integer.toHexString(key.hashCode()),
                        "BigValue(10MB)");
            }
        }
    }

    private static Object field(Object target, Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /**
     * 实验 3：InheritableThreadLocal 在创建子线程时把父线程的值复制过去（Thread 构造器里复制 inheritableThreadLocals）。
     * 但线程池的线程是提前创建好、反复复用的，之后再 set 的值传不过去 —— 这个场景要用阿里的 TransmittableThreadLocal。
     */
    static void inheritable() throws InterruptedException {
        System.out.println("\n== 实验 3：InheritableThreadLocal ==");
        ThreadLocal<String> normal = new ThreadLocal<>();
        InheritableThreadLocal<String> inheritable = new InheritableThreadLocal<>();
        normal.set("父线程的值");
        inheritable.set("父线程的值");
        Thread child = new Thread(() -> System.out.println("子线程：ThreadLocal = " + normal.get()
                + "，InheritableThreadLocal = " + inheritable.get()));
        child.start();
        child.join();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.execute(() -> { });   // 先让线程池把线程创建出来（此时复制的是“父线程的值”）
        inheritable.set("父线程改了新值");
        pool.execute(() -> System.out.println("线程池线程：InheritableThreadLocal = " + inheritable.get() + "  ← 还是旧值"));
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }
}
