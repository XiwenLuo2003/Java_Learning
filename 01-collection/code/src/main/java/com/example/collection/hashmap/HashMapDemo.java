package com.example.collection.hashmap;

import com.example.collection.Reflects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HashMap 原理实验。实验 3、5 用反射读取真实 HashMap 的 table，验证 MiniHashMap 里读到的源码逻辑。
 * <p>
 * VM 参数：--add-opens java.base/java.util=ALL-UNNAMED
 */
public class HashMapDemo {

    public static void main(String[] args) {
        perturbation();
        indexByAnd();
        tableSizeFor();
        resizeSplit();
        treeify();
        equalsWithoutHashCode();
        mutableKey();
    }

    /** 实验 1：扰动函数。hashCode 只在高 16 位不同的 8 个 key，不扰动全部挤在 0 号桶，扰动后分散开 */
    static void perturbation() {
        System.out.println("== 实验 1：扰动函数 (h ^ h >>> 16) ==");
        int n = 16;
        for (int i = 0; i < 8; i++) {
            int h = i << 16;
            int raw = (n - 1) & h;
            int mixed = (n - 1) & (h ^ (h >>> 16));
            System.out.printf("hashCode = 0x%08x  不扰动下标 = %d  扰动后下标 = %d%n", h, raw, mixed);
        }
    }

    /** 实验 2：n 是 2 的幂时 (n - 1) & hash 等价于取模，而且负数 hash 也能得到合法下标 */
    static void indexByAnd() {
        System.out.println("\n== 实验 2：(n - 1) & hash vs hash % n ==");
        int n = 16;
        System.out.println("n - 1 的二进制 = " + Integer.toBinaryString(n - 1) + "，按位与就是只保留 hash 的低 4 位");
        for (int hash : new int[]{5, 21, 100, -17}) {
            System.out.printf("hash = %4d  (n-1)&hash = %2d  hash %% n = %3d%n", hash, (n - 1) & hash, hash % n);
        }
        System.out.println("-17 % 16 = -1 不能当下标，按位与没有这个问题；如果 n 不是 2 的幂（比如 10），n - 1 = 1001，有些桶永远用不到");
    }

    /** 实验 3：构造器传的容量会被调成 2 的幂，而且数组在第一次 put 时才创建 */
    static void tableSizeFor() {
        System.out.println("\n== 实验 3：tableSizeFor ==");
        for (int cap : new int[]{1, 10, 16, 17, 100}) {
            Map<String, String> map = new HashMap<>(cap);
            int before = Reflects.hashMapCapacity(map);
            map.put("k", "v");
            System.out.printf("new HashMap(%3d)：put 前 table 长度 = %d，put 后 = %3d，MiniHashMap.tableSizeFor = %3d%n",
                    cap, before, Reflects.hashMapCapacity(map), MiniHashMap.tableSizeFor(cap));
        }
        // 想放 100 个元素又不想扩容：100 / 0.75 = 133.3，传 134 → 容量 256；直接传 100 → 容量 128，阈值 96，放到第 97 个就扩容
        Map<Integer, Integer> sized = new HashMap<>(100);
        for (int i = 0; i < 100; i++) {
            sized.put(i, i);
        }
        System.out.println("new HashMap(100) 放 100 个元素后容量 = " + Reflects.hashMapCapacity(sized) + "（扩容过一次）");
    }

    /**
     * 实验 4：扩容时链表拆成 lo、hi 两条。
     * Integer 的 hashCode 就是它本身，小整数扰动后不变。5、21、37、53 在容量 16 时都在 5 号桶；
     * 扩容到 32 后看 hash & 16：5 和 37 是 0，留在 5 号桶；21 和 53 是 16，去 5 + 16 = 21 号桶。
     */
    static void resizeSplit() {
        System.out.println("\n== 实验 4：扩容拆分 lo / hi 链 ==");
        MiniHashMap<Integer, String> map = new MiniHashMap<>();
        for (int key : new int[]{5, 21, 37, 53}) {
            map.put(key, "v" + key);
        }
        System.out.println("容量 " + map.capacity() + "，5 号桶 = " + map.bucket(5));
        // 再放 9 个不落在 5、21 号桶的 key，size 到 13 > 阈值 12，触发扩容
        for (int key : new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9}) {
            map.put(key, "filler");
        }
        System.out.println("容量 " + map.capacity() + "，5 号桶 = " + map.bucket(5) + "，21 号桶 = " + map.bucket(21));
        System.out.println("两条链都保持了原来的相对顺序（5 在 37 前，21 在 53 前）");
    }

    /** hashCode 固定的 key，所有实例都落在同一个桶里 */
    record BadKey(int id) {
        @Override
        public int hashCode() {
            return 1;
        }
    }

    /**
     * 实验 5：链表转红黑树的条件。用真实 HashMap 验证：
     * 第 9 个冲突的 key 进来时链表长度超过 8，但容量 16 &lt; 64，只扩容到 32；第 10 个再扩到 64；第 11 个才真正转树。
     */
    static void treeify() {
        System.out.println("\n== 实验 5：转红黑树（真实 HashMap） ==");
        Map<BadKey, Integer> map = new HashMap<>();
        int index = 1; // hash(1) = 1，(n - 1) & 1 = 1，一直在 1 号桶
        for (int i = 1; i <= 11; i++) {
            map.put(new BadKey(i), i);
            if (i >= 8) {
                System.out.printf("放入第 %2d 个冲突的 key：table 长度 = %2d，1 号桶的节点类型 = %s%n",
                        i, Reflects.hashMapCapacity(map), Reflects.binType(map, index));
            }
        }
        System.out.println("MiniHashMap 的同样过程：");
        MiniHashMap<BadKey, Integer> mini = new MiniHashMap<>();
        for (int i = 1; i <= 11; i++) {
            mini.put(new BadKey(i), i);
        }
    }

    /** 只重写 equals、没重写 hashCode：两个“相等”的对象 hashCode 不同，落在不同的桶，HashMap 认为是两个 key */
    static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Point p && p.x == x && p.y == y;
        }
    }

    /** 实验 6：equals 和 hashCode 必须一起重写 */
    static void equalsWithoutHashCode() {
        System.out.println("\n== 实验 6：只重写 equals ==");
        Map<Point, String> map = new HashMap<>();
        map.put(new Point(1, 2), "first");
        map.put(new Point(1, 2), "second");
        System.out.println("equals 为 true，但 size = " + map.size() + "，get 结果 = " + map.get(new Point(1, 2)));
    }

    /** 实验 7：可变对象当 key，put 之后改了它，hash 变了，就再也找不到了 */
    static void mutableKey() {
        System.out.println("\n== 实验 7：可变 key ==");
        List<String> key = new ArrayList<>(List.of("a"));
        Map<List<String>, String> map = new HashMap<>();
        map.put(key, "value");
        key.add("b");
        System.out.println("修改 key 后 get = " + map.get(key) + "，但 size = " + map.size()
                + "，containsValue = " + map.containsValue("value"));
        System.out.println("所以 key 最好用 String、Integer、record 这类不可变对象");
    }
}
