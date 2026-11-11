package com.example.jvm.oom;

import java.util.ArrayList;
import java.util.List;

/**
 * 堆溢出：java.lang.OutOfMemoryError: Java heap space
 * <p>
 * VM 参数：
 * <pre>
 * -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=heapdump.hprof
 * </pre>
 * -Xms 初始堆、-Xmx 最大堆，设成一样避免运行时扩缩容；后两个参数让 OOM 时自动导出堆快照，生产环境必加。
 * <p>
 * 运行后项目目录下会出现 heapdump.hprof，用 IDEA Ultimate（直接打开 .hprof）、VisualVM 或 Eclipse MAT 分析：
 * 看“支配树 / Biggest Objects”，能找到是 HeapOomDemo.CACHE 这个 ArrayList 拉着几百个 byte[]。
 * <p>
 * 典型线上原因：一次查询全表数据到内存、本地缓存无上限、集合只加不删、大文件一次性读入。
 */
public class HeapOomDemo {

    /** 静态集合是 GC Root 能直接到达的，里面的对象永远不会被回收 —— 最常见的内存泄漏 */
    private static final List<byte[]> CACHE = new ArrayList<>();

    public static void main(String[] args) {
        int count = 0;
        try {
            while (true) {
                CACHE.add(new byte[512 * 1024]);
                count++;
            }
        } catch (OutOfMemoryError e) {
            int size = CACHE.size();
            CACHE.clear();   // 先释放内存，否则下面打印字符串都可能再次 OOM
            System.out.println("放了 " + count + " 个 512KB 数组后 OOM：" + e.getMessage() + "（集合大小 " + size + "）");
        }
    }
}
