package com.example.redis.principle;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 用 Java 还原 Redis 的 SDS（Simple Dynamic String，源码 src/sds.h、src/sds.c）。
 * <pre>
 * struct sdshdr8 {
 *     uint8_t len;          // 已用长度：strlen 是 O(1)
 *     uint8_t alloc;        // 分配的容量（不含头和结尾的 '\0'）
 *     unsigned char flags;  // 低 3 位是类型：sdshdr5/8/16/32/64
 *     char buf[];           // 真正的数据，末尾仍然补一个 '\0'，方便复用 C 的字符串函数
 * };
 * </pre>
 * 直接运行 main，不需要 Redis。
 */
public class MiniSds {

    /** sds.h：#define SDS_MAX_PREALLOC (1024*1024) */
    static final int SDS_MAX_PREALLOC = 1024 * 1024;

    private byte[] buf;
    private int len;

    /** sdsnewlen：新建时不预留空间，alloc == len */
    public MiniSds(byte[] init) {
        this.buf = Arrays.copyOf(init, init.length);
        this.len = init.length;
    }

    public int len() {
        return len;
    }

    public int alloc() {
        return buf.length;
    }

    public int avail() {
        return alloc() - len;
    }

    /** sdscatlen：先确保空间够，再拷贝 */
    public void cat(byte[] t) {
        makeRoomFor(t.length);
        System.arraycopy(t, 0, buf, len, t.length);
        len += t.length;
    }

    /**
     * sdsMakeRoomFor 的核心逻辑（Redis 7 里 greedy == 1 的分支）：
     * <pre>
     * if (avail >= addlen) return s;
     * newlen = (len+addlen);
     * if (newlen < SDS_MAX_PREALLOC) newlen *= 2;
     * else newlen += SDS_MAX_PREALLOC;
     * </pre>
     * 真实 Redis 还会把 alloc 调成 jemalloc 实际给的大小（s_malloc_usable），所以 alloc 可能比这里算的略大。
     */
    void makeRoomFor(int addlen) {
        if (avail() >= addlen) {
            return;
        }
        int newlen = len + addlen;
        if (newlen < SDS_MAX_PREALLOC) {
            newlen *= 2;
        } else {
            newlen += SDS_MAX_PREALLOC;
        }
        buf = Arrays.copyOf(buf, newlen);
    }

    /** sdsrange：截取一段。惰性空间释放：只改 len，alloc 不变，下次追加直接复用 */
    public void range(int start, int end) {
        int newLen = end - start + 1;
        System.arraycopy(buf, start, buf, 0, newLen);
        len = newLen;
    }

    /** sdsclear：清空也只是 len = 0 */
    public void clear() {
        len = 0;
    }

    @Override
    public String toString() {
        return new String(buf, 0, len, StandardCharsets.UTF_8);
    }

    /** sdsReqType：按长度挑最小的头，省内存 */
    static String reqType(long length) {
        if (length < 1 << 5) return "sdshdr5（头 1 字节，只用于 key 等不会追加的短串）";
        if (length < 1 << 8) return "sdshdr8（头 3 字节）";
        if (length < 1 << 16) return "sdshdr16（头 5 字节）";
        if (length < 1L << 32) return "sdshdr32（头 9 字节）";
        return "sdshdr64（头 17 字节）";
    }

    private static void print(String step, MiniSds s) {
        System.out.printf("%-34s len=%-8d alloc=%-8d avail=%d%n", step, s.len(), s.alloc(), s.avail());
    }

    public static void main(String[] args) {
        // ===== 实验 1：二进制安全。C 字符串以 '\0' 结尾，strlen 遇到 '\0' 就停；SDS 用 len 判断结尾 =====
        byte[] binary = {'a', 'b', 0, 'c', 'd'};
        MiniSds bin = new MiniSds(binary);
        int cStrlen = 0;
        while (cStrlen < binary.length && binary[cStrlen] != 0) {
            cStrlen++;
        }
        System.out.println("1. 中间带 \\0 的 5 个字节：C strlen = " + cStrlen + "，SDS len = " + bin.len());
        // 预期：C strlen = 2，SDS len = 5。所以 Redis 能存图片、序列化对象、protobuf 等任意字节

        // ===== 实验 2：空间预分配。小于 1MB 时翻倍，大于等于 1MB 时每次多给 1MB =====
        System.out.println("\n2. 空间预分配");
        MiniSds s = new MiniSds("redis".getBytes(StandardCharsets.UTF_8));
        print("   new \"redis\"", s);                     // len=5  alloc=5   新建不预留
        s.cat(" is fast".getBytes(StandardCharsets.UTF_8));
        print("   cat \" is fast\"", s);                  // len=13 alloc=26  (5+8)*2
        s.cat("!".getBytes(StandardCharsets.UTF_8));
        print("   cat \"!\"（空间够，不扩容）", s);        // len=14 alloc=26
        s.cat(new byte[20]);
        print("   cat 20 字节", s);                        // len=34 alloc=68  (14+20)*2

        MiniSds big = new MiniSds(new byte[512 * 1024]);
        big.cat(new byte[600 * 1024]);
        print("   512KB 再追加 600KB", big);              // len=1138688 alloc=2187264  1112KB + 1MB
        // 结论：N 次追加最多扩容 N 次，实际远少于 N 次；但 APPEND 用多了会有一半空间是空的，这是用内存换时间

        // ===== 实验 3：惰性空间释放 =====
        System.out.println("\n3. 惰性空间释放");
        s.range(0, 4);
        print("   sdsrange 只保留 \"redis\"", s);        // len=5 alloc=68，空间不还给系统
        s.cat(" 7".getBytes(StandardCharsets.UTF_8));
        print("   再 cat \" 7\"（直接复用）", s);          // len=7 alloc=68
        System.out.println("   内容：" + s);

        // ===== 实验 4：按长度选择头部类型 =====
        System.out.println("\n4. 头部类型");
        for (long n : new long[]{10, 100, 1000, 100_000, 5_000_000_000L}) {
            System.out.printf("   长度 %-12d → %s%n", n, reqType(n));
        }
    }
}
