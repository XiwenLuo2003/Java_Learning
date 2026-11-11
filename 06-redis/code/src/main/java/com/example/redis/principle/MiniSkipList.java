package com.example.redis.principle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用 Java 还原 ZSet 的跳表（源码 src/server.h 的 zskiplist、src/t_zset.c 的 zslInsert / zslGetRank）。
 * <p>
 * ZSet 元素多了以后的编码是 skiplist，实际是“跳表 + 字典”两个结构：
 * 字典 member → score 让 ZSCORE 是 O(1)；跳表按 (score, member) 排序，支持 ZRANGE / ZRANK / ZRANGEBYSCORE。
 * 直接运行 main，不需要 Redis。
 */
public class MiniSkipList {

    /** server.h：#define ZSKIPLIST_MAXLEVEL 32 */
    static final int MAX_LEVEL = 32;
    /** server.h：#define ZSKIPLIST_P 0.25，每升一层的概率 */
    static final double P = 0.25;

    static final class Node {
        final String member;
        final double score;
        Node backward;               // 只有第 0 层有后退指针，用于 ZREVRANGE
        final Node[] forward;
        final int[] span;            // 这一层跳到下一个节点跨过了几个节点，累加起来就是排名

        Node(int level, double score, String member) {
            this.member = member;
            this.score = score;
            this.forward = new Node[level];
            this.span = new int[level];
        }
    }

    private final Node header = new Node(MAX_LEVEL, 0, null);
    private Node tail;
    private int length;
    private int level = 1;

    /** 统计查找时比较了几次，用来观察 O(logN) */
    long comparisons;

    /** zslRandomLevel：每层有 1/4 的概率继续往上，所以平均层数 1/(1-P) ≈ 1.33，大部分节点只有 1 层 */
    static int randomLevel() {
        int lvl = 1;
        while ((ThreadLocalRandom.current().nextInt() & 0xFFFF) < P * 0xFFFF) {
            lvl++;
        }
        return Math.min(lvl, MAX_LEVEL);
    }

    /** 排序规则：先比 score，score 相同再按 member 字典序，所以同分的元素顺序是确定的 */
    private boolean less(Node x, double score, String member) {
        comparisons++;
        return x.score < score || (x.score == score && x.member.compareTo(member) < 0);
    }

    /** zslInsert：从最高层往下找，update[i] 记录每一层的前驱，rank[i] 记录前驱的排名 */
    public void insert(double score, String member) {
        Node[] update = new Node[MAX_LEVEL];
        int[] rank = new int[MAX_LEVEL];
        Node x = header;
        for (int i = level - 1; i >= 0; i--) {
            rank[i] = (i == level - 1) ? 0 : rank[i + 1];
            while (x.forward[i] != null && less(x.forward[i], score, member)) {
                rank[i] += x.span[i];
                x = x.forward[i];
            }
            update[i] = x;
        }

        int lvl = randomLevel();
        if (lvl > level) {
            for (int i = level; i < lvl; i++) {
                rank[i] = 0;
                update[i] = header;
                update[i].span[i] = length;
            }
            level = lvl;
        }

        x = new Node(lvl, score, member);
        for (int i = 0; i < lvl; i++) {
            x.forward[i] = update[i].forward[i];
            update[i].forward[i] = x;
            // rank[0] - rank[i] 是第 i 层前驱到新节点之间隔了几个节点
            x.span[i] = update[i].span[i] - (rank[0] - rank[i]);
            update[i].span[i] = (rank[0] - rank[i]) + 1;
        }
        // 新节点没有达到的高层，前驱的跨度要 +1（中间多了一个节点）
        for (int i = lvl; i < level; i++) {
            update[i].span[i]++;
        }

        x.backward = (update[0] == header) ? null : update[0];
        if (x.forward[0] != null) {
            x.forward[0].backward = x;
        } else {
            tail = x;
        }
        length++;
    }

    /** zslGetRank：沿途把 span 加起来就是排名（从 1 开始），所以 ZRANK 是 O(logN) 而不是 O(N) */
    public int rank(double score, String member) {
        int r = 0;
        Node x = header;
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null
                    && (x.forward[i].score < score
                    || (x.forward[i].score == score && x.forward[i].member.compareTo(member) <= 0))) {
                comparisons++;
                r += x.span[i];
                x = x.forward[i];
            }
            if (x.member != null && x.member.equals(member)) {
                return r;
            }
        }
        return 0;
    }

    /** ZRANGEBYSCORE：先 O(logN) 找到第一个 >= min 的节点，再沿第 0 层链表往后扫 */
    public List<String> rangeByScore(double min, double max) {
        Node x = header;
        for (int i = level - 1; i >= 0; i--) {
            while (x.forward[i] != null && x.forward[i].score < min) {
                x = x.forward[i];
            }
        }
        List<String> result = new ArrayList<>();
        x = x.forward[0];
        while (x != null && x.score <= max) {
            result.add(x.member + ":" + (long) x.score);
            x = x.forward[0];
        }
        return result;
    }

    /** ZREVRANGE 0 n-1：从 tail 沿 backward 往前走 */
    public List<String> revTop(int n) {
        List<String> result = new ArrayList<>();
        for (Node x = tail; x != null && result.size() < n; x = x.backward) {
            result.add(x.member + ":" + (long) x.score);
        }
        return result;
    }

    /** 画出每一层，箭头上的数字是 span（只适合元素很少时） */
    public void print() {
        for (int i = level - 1; i >= 0; i--) {
            StringBuilder sb = new StringBuilder(String.format("L%d head", i));
            for (Node prev = header, x = header.forward[i]; x != null; prev = x, x = x.forward[i]) {
                sb.append(" -").append(prev.span[i]).append("-> ").append(x.member).append(':').append((long) x.score);
            }
            System.out.println("   " + sb);
        }
    }

    public static void main(String[] args) {
        // ===== 实验 1：小跳表的结构。每次运行层高随机，画出来的图会不一样 =====
        MiniSkipList zset = new MiniSkipList();
        String[] names = {"tom", "jack", "lucy", "lily", "bob", "amy", "mike", "kate"};
        int[] scores = {88, 95, 70, 95, 60, 99, 82, 75};
        for (int i = 0; i < names.length; i++) {
            zset.insert(scores[i], names[i]);
        }
        System.out.println("1. 8 个元素的跳表，最高 " + zset.level + " 层：");
        zset.print();

        // ===== 实验 2：排行榜三件套 =====
        System.out.println("\n2. ZRANK jack（正序排名，从 1 开始）= " + zset.rank(95, "jack"));       // 预期 6：60 70 75 82 88 95(jack) 95(lily) 99
        System.out.println("   ZRANK lily = " + zset.rank(95, "lily") + "（同分按 member 字典序，jack < lily）"); // 预期 7
        System.out.println("   ZRANGEBYSCORE 75 90 = " + zset.rangeByScore(75, 90));              // [kate:75, mike:82, tom:88]
        System.out.println("   ZREVRANGE 0 2（Top3）= " + zset.revTop(3));                         // [amy:99, lily:95, jack:95]

        // ===== 实验 3：层高分布。P=0.25 时约 75% 的节点 1 层，18.75% 2 层，4.7% 3 层 ... =====
        int[] histogram = new int[MAX_LEVEL + 1];
        int n = 1_000_000;
        long totalLevels = 0;
        for (int i = 0; i < n; i++) {
            int lvl = randomLevel();
            histogram[lvl]++;
            totalLevels += lvl;
        }
        System.out.println("\n3. 100 万个节点的层高分布：");
        for (int lvl = 1; lvl <= 6; lvl++) {
            System.out.printf("   %d 层：%.2f%%%n", lvl, histogram[lvl] * 100.0 / n);
        }
        System.out.printf("   平均每个节点 %.3f 个指针（理论值 1/(1-0.25) = 1.333），比红黑树每个节点固定 2 个指针还省%n",
                totalLevels * 1.0 / n);

        // ===== 实验 4：查找步数 ≈ O(logN)。元素翻 4 倍，步数只多几步 =====
        System.out.println();
        for (int size : new int[]{10_000, 40_000, 160_000}) {
            MiniSkipList large = new MiniSkipList();
            for (int i = 0; i < size; i++) {
                large.insert(i, "m" + i);
            }
            large.comparisons = 0;
            int lookups = 1000;
            for (int i = 0; i < lookups; i++) {
                int target = ThreadLocalRandom.current().nextInt(size);
                large.rank(target, "m" + target);
            }
            System.out.printf("4. %,d 个元素：平均每次 ZRANK 向前跳 %d 步%n", size, large.comparisons / lookups);
        }
        // 预期：元素每翻 4 倍，步数只多 3 步左右（多一层，每层平均跳 (1-P)/P = 3 步），而不是像链表那样也翻 4 倍
    }
}
