package com.example.jvm.gc;

/**
 * 对象分配与晋升的三个经典实验（改编自《深入理解 Java 虚拟机》3.8 节，日志参数换成 JDK 9+ 的统一日志 -Xlog）。
 * <p>
 * 用 Serial 收集器是因为它的分代结构最“教科书”，日志最好读。公共 VM 参数：
 * <pre>
 * -XX:+UseSerialGC -Xms20m -Xmx20m -Xmn10m -XX:SurvivorRatio=8 -Xlog:gc*
 * </pre>
 * 含义：堆 20MB，新生代 10MB（Eden 8MB + 两个 Survivor 各 1MB），老年代 10MB；打印 GC 日志和退出时的堆布局。
 * <p>
 * 运行方式：Program arguments 填 minor / pretenure / tenure 选择实验，各实验额外的 VM 参数见方法注释。
 * 日志里重点看：def new generation（新生代）的 eden、from、to，tenured generation（老年代）的 used。
 */
public class AllocationDemo {

    private static final int MB = 1024 * 1024;

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "minor";
        switch (mode) {
            case "minor" -> minorGc();
            case "pretenure" -> pretenure();
            case "tenure" -> tenure();
            default -> System.out.println("参数只能是 minor / pretenure / tenure");
        }
    }

    /**
     * 实验 1：对象优先在 Eden 分配，Eden 不够触发 Minor GC；Survivor 放不下就通过分配担保直接进老年代。
     * <p>
     * 过程：a1、a2、a3 共 6MB 放进 Eden；分配 a4（4MB）时 Eden（8MB）剩不到 4MB，触发 Minor GC。
     * 三个 2MB 对象都还活着，但 Survivor 只有 1MB 放不下，只好全部搬进老年代。GC 后 a4 放进 Eden。
     * <p>
     * 预期退出时：eden 用了约 4MB（a4），tenured 用了约 6MB（a1~a3）。
     */
    static void minorGc() {
        byte[] a1 = new byte[2 * MB];
        byte[] a2 = new byte[2 * MB];
        byte[] a3 = new byte[2 * MB];
        System.out.println("已分配 6MB，下面分配 4MB 会触发 Minor GC");
        byte[] a4 = new byte[4 * MB];
        keep(a1, a2, a3, a4);
    }

    /**
     * 实验 2：大对象直接进入老年代，避免在 Eden 和两个 Survivor 之间来回复制。
     * 额外 VM 参数：-XX:PretenureSizeThreshold=3145728（3MB，只对 Serial 和 ParNew 有效；G1 里超过半个 Region 的叫 Humongous 对象，单独存放）
     * <p>
     * 预期：没有发生 GC，退出时 eden 几乎没用，tenured 用了约 4MB。
     */
    static void pretenure() {
        byte[] big = new byte[4 * MB];
        keep(big);
    }

    /**
     * 实验 3：长期存活的对象进入老年代。每熬过一次 Minor GC 年龄 + 1，达到 MaxTenuringThreshold 就晋升。
     * 额外 VM 参数：-XX:MaxTenuringThreshold=1 -Xlog:gc+age=trace
     * <p>
     * 过程：a1（256KB）能放进 Survivor。第一次 GC：a1 进 Survivor，年龄 1；第二次 GC：年龄达到阈值 1，晋升到老年代。
     * 预期：第二次 GC 后 from 区（Survivor）变成 0，a1 在老年代。把阈值改成 15 再跑，a1 会留在 Survivor。
     */
    static void tenure() {
        byte[] a1 = new byte[MB / 4];
        byte[] a2 = new byte[4 * MB];
        byte[] a3 = new byte[4 * MB];   // 第一次 Minor GC
        a3 = null;
        a3 = new byte[4 * MB];          // 第二次 Minor GC
        keep(a1, a2, a3);
    }

    /** 让对象在方法结束前一直“有用”，防止被 JIT 认为是死对象提前回收 */
    private static void keep(Object... objects) {
        System.out.println("持有 " + objects.length + " 个对象，程序结束时打印堆布局");
    }
}
