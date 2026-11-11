package com.example.collection.hashmap;

import java.util.ArrayList;
import java.util.List;

/**
 * JDK 17 java.util.HashMap 的精简版，用来逐行读懂 putVal() 和 resize()。
 * <p>
 * 保留了源码的变量名和写法（包括“在 if 里赋值”的风格），方便和 IDEA 里的源码对照：
 * 在 IDEA 里按两下 Shift 搜 HashMap，打开后 Ctrl+F12 找到 putVal / resize。
 * <p>
 * 省略的部分：红黑树（TreeNode）、序列化、视图（keySet 等）、remove。
 * 链表长度超过阈值时只打印“这里会转红黑树”，仍按链表存。
 */
public class MiniHashMap<K, V> {

    /** 默认初始容量 16，必须是 2 的幂 */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;
    static final int MAXIMUM_CAPACITY = 1 << 30;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    /** 链表转红黑树的阈值 */
    static final int TREEIFY_THRESHOLD = 8;
    /** 转红黑树要求的最小 table 容量，不到 64 优先扩容 */
    static final int MIN_TREEIFY_CAPACITY = 64;

    static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    Node<K, V>[] table;
    int size;
    /** 下次扩容的阈值 = 容量 * 负载因子；table 还没创建时，暂存构造器算出来的初始容量 */
    int threshold;
    final float loadFactor;
    /** 打印扩容、转树等过程 */
    boolean verbose = true;

    public MiniHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    public MiniHashMap(int initialCapacity) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.threshold = tableSizeFor(initialCapacity);
    }

    /**
     * 扰动函数：高 16 位异或低 16 位。
     * 下标是 (n - 1) &amp; hash，n 较小时只有低几位参与运算，高位的差异完全被丢掉；
     * 把高 16 位混进低 16 位，让高位也影响下标，减少冲突。null 的 hash 固定是 0，放在 0 号桶。
     */
    static int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /**
     * 返回 &gt;= cap 的最小的 2 的幂。new HashMap(10) 实际容量是 16，new HashMap(17) 是 32。
     * -1 &gt;&gt;&gt; numberOfLeadingZeros(cap - 1) 得到“cap - 1 的最高位及以下全是 1”的数，再加 1。
     */
    static int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    public V put(K key, V value) {
        return putVal(hash(key), key, value);
    }

    /** 对应源码 putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) */
    V putVal(int hash, K key, V value) {
        Node<K, V>[] tab;
        Node<K, V> p;
        int n, i;
        // 第 1 步：table 为空就先初始化（懒加载，new HashMap() 时并不创建数组）
        if ((tab = table) == null || (n = tab.length) == 0) {
            n = (tab = resize()).length;
        }
        // 第 2 步：算下标 (n - 1) & hash；桶是空的，直接放新节点
        if ((p = tab[i = (n - 1) & hash]) == null) {
            tab[i] = new Node<>(hash, key, value, null);
        } else {
            Node<K, V> e;
            K k;
            // 第 3 步：桶不空。先比头节点：hash 相等且 key 相等（== 或 equals），说明是同一个 key
            if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k)))) {
                e = p;
            }
            // 源码这里还有一个分支：头节点是 TreeNode，就调用 putTreeVal 按红黑树插入（本类省略）
            else {
                // 第 4 步：遍历链表
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        // 走到尾部还没找到相同的 key：尾插（JDK 7 是头插）
                        p.next = new Node<>(hash, key, value, null);
                        // binCount 从 0 开始，此时新节点是链表的第 binCount + 2 个；
                        // binCount >= 7 即新节点是第 9 个，链表长度超过 8，尝试转红黑树
                        if (binCount >= TREEIFY_THRESHOLD - 1) {
                            treeifyBin(tab, hash);
                        }
                        break;
                    }
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        break;
                    }
                    p = e;
                }
            }
            // 第 5 步：找到了相同的 key，覆盖 value 并返回旧值；这种情况 size 不变，也不会触发扩容
            if (e != null) {
                V oldValue = e.value;
                e.value = value;
                return oldValue;
            }
        }
        // 第 6 步：新增了节点，size + 1，超过阈值就扩容（先插入，后扩容；JDK 7 是先扩容再插入）
        if (++size > threshold) {
            resize();
        }
        return null;
    }

    /** 源码：链表太长时调用。容量不到 64 时不转树，而是扩容（扩容能把链表拆短，代价比维护红黑树小） */
    void treeifyBin(Node<K, V>[] tab, int hash) {
        int n = tab.length;
        if (n < MIN_TREEIFY_CAPACITY) {
            log("链表长度超过 8，但容量 %d < 64，先扩容", n);
            resize();
        } else {
            log("链表长度超过 8 且容量 %d >= 64，这里会把桶 %d 转成红黑树（本类省略，仍按链表存）",
                    n, (n - 1) & hash);
        }
    }

    /**
     * 初始化或扩容为 2 倍。
     * 关键优化：扩容后不用重新算 hash，节点只有两种去处 ——
     * (e.hash &amp; oldCap) == 0 留在原下标 j（lo 链），否则去 j + oldCap（hi 链）。
     * 因为新下标比旧下标只多看 hash 的一位，也就是 oldCap 对应的那一位。
     */
    final Node<K, V>[] resize() {
        Node<K, V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            } else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY) {
                // 正常扩容：容量和阈值都翻倍
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            // new HashMap(10)：构造器把 tableSizeFor(10) = 16 暂存在 threshold 里，这里拿出来当容量
            newCap = oldThr;
        } else {
            // new HashMap()：默认容量 16，阈值 12
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int) (DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
        @SuppressWarnings("unchecked")
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        table = newTab;
        log("resize：容量 %d → %d，阈值 → %d", oldCap, newCap, newThr);
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K, V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    if (e.next == null) {
                        // 桶里只有一个节点，直接算新下标
                        newTab[e.hash & (newCap - 1)] = e;
                    } else {
                        // 链表拆成 lo、hi 两条，各自保持原来的相对顺序（尾插），所以 JDK 8 并发扩容不会成环
                        Node<K, V> loHead = null, loTail = null;
                        Node<K, V> hiHead = null, hiTail = null;
                        Node<K, V> next;
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null) {
                                    loHead = e;
                                } else {
                                    loTail.next = e;
                                }
                                loTail = e;
                            } else {
                                if (hiTail == null) {
                                    hiHead = e;
                                } else {
                                    hiTail.next = e;
                                }
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }

    public V get(Object key) {
        Node<K, V>[] tab;
        Node<K, V> first, e;
        int n, hash;
        K k;
        if ((tab = table) != null && (n = tab.length) > 0
                && (first = tab[(n - 1) & (hash = hash(key))]) != null) {
            if (first.hash == hash && ((k = first.key) == key || (key != null && key.equals(k)))) {
                return first.value;
            }
            if ((e = first.next) != null) {
                do {
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        return e.value;
                    }
                } while ((e = e.next) != null);
            }
        }
        return null;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return table == null ? 0 : table.length;
    }

    /** 某个桶里的 key，按链表顺序 */
    public List<K> bucket(int index) {
        List<K> keys = new ArrayList<>();
        for (Node<K, V> e = table[index]; e != null; e = e.next) {
            keys.add(e.key);
        }
        return keys;
    }

    private void log(String format, Object... args) {
        if (verbose) {
            System.out.println("  [MiniHashMap] " + String.format(format, args));
        }
    }
}
