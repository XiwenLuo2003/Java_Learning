package com.example.redis.principle;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * 布隆过滤器：一个 m 位的位数组 + k 个哈希函数。
 * <ul>
 *   <li>add：k 个哈希值对应的位全部置 1</li>
 *   <li>mightContain：k 个位全是 1 才返回 true；有一个是 0 就一定不存在</li>
 * </ul>
 * 位数和哈希个数的公式、“两个哈希模拟 k 个哈希”的做法都和 Guava BloomFilter、Redisson RBloomFilter 一致。
 * Redisson 的版本就是把这个位数组放进 Redis 的 String（SETBIT / GETBIT），多个服务共享。
 * 直接运行 main，不需要 Redis。
 */
public class MiniBloomFilter {

    private final BitSet bits;
    private final long m;
    private final int k;

    public MiniBloomFilter(long expectedInsertions, double fpp) {
        this.m = optimalNumOfBits(expectedInsertions, fpp);
        this.k = optimalNumOfHashFunctions(expectedInsertions, m);
        this.bits = new BitSet((int) m);
    }

    /** m = -n·ln(p) / (ln2)² */
    static long optimalNumOfBits(long n, double p) {
        return (long) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    /** k = (m/n)·ln2 */
    static int optimalNumOfHashFunctions(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    public void add(String key) {
        long hash64 = hash64(key);
        int h1 = (int) hash64;
        int h2 = (int) (hash64 >>> 32);
        for (int i = 1; i <= k; i++) {
            bits.set(index(h1, h2, i));
        }
    }

    public boolean mightContain(String key) {
        long hash64 = hash64(key);
        int h1 = (int) hash64;
        int h2 = (int) (hash64 >>> 32);
        for (int i = 1; i <= k; i++) {
            if (!bits.get(index(h1, h2, i))) {
                return false;
            }
        }
        return true;
    }

    /** Kirsch-Mitzenmacher：g_i(x) = h1(x) + i·h2(x)，效果接近 k 个独立哈希 */
    private int index(int h1, int h2, int i) {
        int combined = h1 + i * h2;
        if (combined < 0) {
            combined = ~combined;
        }
        return (int) (combined % m);
    }

    /** FNV-1a 64 位 + MurmurHash3 的 fmix64 打散 */
    private static long hash64(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            h ^= b;
            h *= 0x100000001b3L;
        }
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    public static void main(String[] args) {
        int n = 1_000_000;
        double fpp = 0.01;
        MiniBloomFilter filter = new MiniBloomFilter(n, fpp);

        // ===== 实验 1：参数计算 =====
        System.out.printf("1. 预计 %,d 个元素、误判率 %.0f%% → 需要 %,d 位（约 %.2f MB），%d 个哈希函数%n",
                n, fpp * 100, filter.m, filter.m / 8.0 / 1024 / 1024, filter.k);
        // 预期：约 958 万位 ≈ 1.14 MB，7 个哈希。同样 100 万个 Long 放 HashSet 要几十 MB

        for (int i = 0; i < n; i++) {
            filter.add("shop:" + i);
        }

        // ===== 实验 2：存在的一定判断为存在（没有假阴性）=====
        int falseNegative = 0;
        for (int i = 0; i < n; i++) {
            if (!filter.mightContain("shop:" + i)) {
                falseNegative++;
            }
        }
        System.out.println("2. 已加入的 100 万个 key 中，被判为“不存在”的个数 = " + falseNegative);   // 预期 0

        // ===== 实验 3：不存在的可能被误判为存在（假阳性），比例接近设定的 1% =====
        int falsePositive = 0;
        for (int i = 0; i < n; i++) {
            if (filter.mightContain("attack:" + i)) {
                falsePositive++;
            }
        }
        System.out.printf("3. 100 万个从没加入的 key，误判为存在的比例 = %.3f%%%n", falsePositive * 100.0 / n);  // 预期 1% 左右

        // ===== 实验 4：塞超了，误判率飙升 =====
        MiniBloomFilter small = new MiniBloomFilter(100_000, fpp);
        for (int i = 0; i < n; i++) {
            small.add("shop:" + i);
        }
        int fp2 = 0;
        for (int i = 0; i < 100_000; i++) {
            if (small.mightContain("attack:" + i)) {
                fp2++;
            }
        }
        System.out.printf("4. 按 10 万容量建、实际塞 100 万：误判率 = %.1f%%（几乎全判存在，过滤器失效）%n", fp2 * 100.0 / 100_000);
        // 所以要按业务增长预估容量，数据量涨上来后重建；另外普通布隆过滤器不能删除元素（一位可能被多个元素共用）
    }
}
