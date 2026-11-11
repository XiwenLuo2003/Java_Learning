package com.example.collection.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * JDK 8+ ConcurrentHashMap#putVal 的精简版：空桶 CAS，非空桶 synchronized 锁头节点。
 * <p>
 * 源码用 Unsafe 直接对数组元素做 volatile 读和 CAS（tabAt / casTabAt），这里用 AtomicReferenceArray 代替，语义相同。
 * 省略的部分：扩容（transfer、helpTransfer、ForwardingNode）、红黑树（TreeBin）、remove。
 */
public class MiniConcurrentHashMap<K, V> {

    /** 源码：hash 最高位留给特殊节点（MOVED = -1 表示正在迁移，TREEBIN = -2 表示红黑树），普通节点 hash 一定是正数 */
    static final int HASH_BITS = 0x7fffffff;

    static class Node<K, V> {
        final int hash;
        final K key;
        /** val 和 next 都是 volatile：get 不加锁也能看到其他线程写入的最新值 */
        volatile V val;
        volatile Node<K, V> next;

        Node(int hash, K key, V val) {
            this.hash = hash;
            this.key = key;
            this.val = val;
        }
    }

    private final AtomicReferenceArray<Node<K, V>> table;
    /** 源码的 baseCount + CounterCell[] 和 LongAdder 是同一个思路：竞争激烈时分散到多个格子上计数，求和得到 size */
    private final LongAdder count = new LongAdder();

    // 演示用的统计
    final AtomicInteger casSuccess = new AtomicInteger();
    final AtomicInteger casFailure = new AtomicInteger();
    final AtomicInteger lockedPut = new AtomicInteger();

    public MiniConcurrentHashMap(int capacity) {
        if (Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("容量必须是 2 的幂");
        }
        this.table = new AtomicReferenceArray<>(capacity);
    }

    static int spread(int h) {
        return (h ^ (h >>> 16)) & HASH_BITS;
    }

    public V put(K key, V value) {
        // 不允许 null：get 返回 null 时无法区分“没有这个 key”和“value 就是 null”，
        // 单线程的 HashMap 可以再调 containsKey 确认，并发下两次调用之间数据可能已经变了
        if (key == null || value == null) {
            throw new NullPointerException();
        }
        int hash = spread(key.hashCode());
        // 源码：for (Node<K,V>[] tab = table;;) —— 自旋，CAS 失败或者头节点变了就重来
        for (; ; ) {
            int n = table.length();
            int i = (n - 1) & hash;
            Node<K, V> f = table.get(i);
            if (f == null) {
                // 空桶：不加锁，CAS 把新节点放成头节点
                if (table.compareAndSet(i, null, new Node<>(hash, key, value))) {
                    casSuccess.incrementAndGet();
                    break;
                }
                // CAS 失败：别的线程抢先放了头节点，下一轮走加锁分支
                casFailure.incrementAndGet();
            }
            // 源码这里还有：else if ((fh = f.hash) == MOVED) tab = helpTransfer(tab, f);
            // 发现这个桶正在被扩容迁移，当前线程就去帮忙迁移，迁完再插入
            else {
                V oldVal = null;
                boolean done = false;
                // 非空桶：只锁这一个桶的头节点，其他桶的读写不受影响（锁粒度 = 一个桶）
                synchronized (f) {
                    // 拿到锁后再确认头节点没变（可能刚被扩容迁走或被删除），变了就重试
                    if (table.get(i) == f) {
                        Node<K, V> e = f;
                        while (true) {
                            if (e.hash == hash && (e.key == key || key.equals(e.key))) {
                                oldVal = e.val;
                                e.val = value;
                                break;
                            }
                            Node<K, V> pred = e;
                            if ((e = e.next) == null) {
                                pred.next = new Node<>(hash, key, value);
                                break;
                            }
                        }
                        done = true;
                    }
                }
                if (done) {
                    lockedPut.incrementAndGet();
                    if (oldVal != null) {
                        return oldVal;
                    }
                    break;
                }
            }
        }
        // 源码：addCount(1L, binCount)，计数 + 检查是否超过 sizeCtl 需要扩容
        count.increment();
        return null;
    }

    /** get 全程不加锁：table.get 是 volatile 读，Node 的 val、next 也是 volatile */
    public V get(Object key) {
        int hash = spread(key.hashCode());
        for (Node<K, V> e = table.get((table.length() - 1) & hash); e != null; e = e.next) {
            if (e.hash == hash && (e.key == key || key.equals(e.key))) {
                return e.val;
            }
        }
        return null;
    }

    public long size() {
        return count.sum();
    }
}
