package com.example.collection.hashmap;

import java.util.HashSet;
import java.util.Set;

/**
 * 复现 JDK 7 HashMap 并发扩容导致链表成环 + 丢数据。
 * <p>
 * 真的开两个线程去撞概率很低，这里用单线程“手动安排”两个线程的执行顺序，每次运行结果都一样，可以打断点一步步看。
 * <p>
 * JDK 7 HashMap#transfer 源码（扩容时把旧表的节点搬到新表）：
 * <pre>
 * void transfer(Entry[] newTable, boolean rehash) {
 *     int newCapacity = newTable.length;
 *     for (Entry&lt;K,V&gt; e : table) {
 *         while (null != e) {
 *             Entry&lt;K,V&gt; next = e.next;          // ① 线程 1 执行完这一句被挂起
 *             int i = indexFor(e.hash, newCapacity);
 *             e.next = newTable[i];               // ② 头插：新节点指向桶里原来的头
 *             newTable[i] = e;                    // ③ 自己成为新的头
 *             e = next;
 *         }
 *     }
 * }
 * </pre>
 * 问题根源：头插法会把链表顺序反过来，而两个线程共享同一批 Entry 对象。
 */
public class Jdk7ResizeLoopDemo {

    static class Entry {
        final int key;
        Entry next;

        Entry(int key, Entry next) {
            this.key = key;
            this.next = next;
        }

        @Override
        public String toString() {
            return String.valueOf(key);
        }
    }

    /** 上面源码里 ②③ 两步：把 e 头插进新表 */
    static void moveToNewTable(Entry e, Entry[] newTable) {
        int i = e.key & (newTable.length - 1);
        e.next = newTable[i];
        newTable[i] = e;
    }

    /** 完整的 transfer，线程 2 用它一口气做完扩容 */
    static void transfer(Entry[] oldTable, Entry[] newTable) {
        for (Entry e : oldTable) {
            while (e != null) {
                Entry next = e.next;
                moveToNewTable(e, newTable);
                e = next;
            }
        }
    }

    public static void main(String[] args) {
        // 旧表容量 2，key 的 hash 就是 key 本身。3、7、5 都在 1 号桶：3 -> 7 -> 5
        Entry e5 = new Entry(5, null);
        Entry e7 = new Entry(7, e5);
        Entry e3 = new Entry(3, e7);
        Entry[] oldTable = {null, e3};
        System.out.println("扩容前（容量 2）：1 号桶 = " + chain(e3));
        System.out.println("扩容到 4 后的正确结果应该是：1 号桶 = 5，3 号桶 = 7、3 两个节点\n");

        // 线程 1：开始扩容，执行完 ① 就被挂起。它记住了 e = 3，next = 7
        Entry t1E = oldTable[1];
        Entry t1Next = t1E.next;
        System.out.println("线程 1：e = " + t1E + "，next = " + t1Next + "，然后被挂起");

        // 线程 2：完整执行扩容。头插导致顺序反转：3 号桶变成 7 -> 3，此时 7.next = 3，3.next = null
        Entry[] t2Table = new Entry[4];
        transfer(oldTable, t2Table);
        System.out.println("线程 2：扩容完成，3 号桶 = " + chain(t2Table[3]) + "，1 号桶 = " + chain(t2Table[1]));
        System.out.println("        注意节点是共享的：现在 7.next = " + e7.next + "，3.next = " + e3.next);

        // 线程 1 恢复执行，用的是自己的新表，但读到的 next 指针已经被线程 2 改过了
        Entry[] t1Table = new Entry[4];
        Entry e = t1E;
        Entry next = t1Next;
        moveToNewTable(e, t1Table);          // 3 放进 3 号桶：3 -> null
        e = next;                            // e = 7
        while (e != null) {
            next = e.next;                   // 第一轮：7.next 已被线程 2 改成 3；第二轮：3.next = null
            moveToNewTable(e, t1Table);      // 第一轮：7 -> 3；第二轮：3.next = 7，成环！
            e = next;
        }
        System.out.println("\n线程 1：扩容完成");
        System.out.println("  3 号桶 = " + chain(t1Table[3]));
        System.out.println("  1 号桶 = " + chain(t1Table[1]) + "  ← 5 丢了：线程 1 读到的 7.next 已经不是 5");
        System.out.println("\n之后任何线程 get 一个落在 3 号桶、但不存在的 key（比如 11），就会在 3 -> 7 -> 3 上无限循环，CPU 100%。");
        System.out.println("JDK 8 改成尾插 + lo/hi 两条链保持原顺序，不会再成环，但 HashMap 依然不是线程安全的（会丢数据），并发场景用 ConcurrentHashMap。");
    }

    /** 打印链表，遇到重复节点说明有环 */
    static String chain(Entry head) {
        if (head == null) {
            return "空";
        }
        StringBuilder sb = new StringBuilder();
        Set<Entry> seen = new HashSet<>();
        for (Entry e = head; e != null; e = e.next) {
            if (!seen.add(e)) {
                return sb.append(e).append(" -> ...（检测到环！）").toString();
            }
            sb.append(e).append(" -> ");
        }
        return sb.append("null").toString();
    }
}
