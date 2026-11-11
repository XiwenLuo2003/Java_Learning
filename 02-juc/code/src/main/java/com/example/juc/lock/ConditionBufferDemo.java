package com.example.juc.lock;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.example.juc.Sleeper.sleep;

/**
 * 有界缓冲区（生产者-消费者），代码来自 java.util.concurrent.locks.Condition 接口 Javadoc 里的官方示例 BoundedBuffer，
 * ArrayBlockingQueue 的 put / take 也是同样的结构（一把锁 + notFull、notEmpty 两个 Condition）。
 * <p>
 * 为什么要两个 Condition：生产者只等“不满”，消费者只等“不空”。缓冲区满了时 put 只唤醒消费者，
 * 不会像 synchronized + notifyAll 那样把其他生产者也叫醒。
 * <p>
 * Condition 原理：每个 Condition 有一条自己的等待队列（AQS 的 ConditionObject）。
 * await：把当前线程加入条件队列 → 完全释放锁 → park；signal：把条件队列的第一个节点转移到 AQS 的同步队列，等着重新抢锁。
 */
public class ConditionBufferDemo {

    static class BoundedBuffer<E> {
        final Lock lock = new ReentrantLock();
        final Condition notFull = lock.newCondition();
        final Condition notEmpty = lock.newCondition();

        final Object[] items;
        int putptr, takeptr, count;

        BoundedBuffer(int capacity) {
            items = new Object[capacity];
        }

        public void put(E x) throws InterruptedException {
            lock.lock();
            try {
                while (count == items.length) {
                    notFull.await();
                }
                items[putptr] = x;
                if (++putptr == items.length) {
                    putptr = 0;
                }
                ++count;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public E take() throws InterruptedException {
            lock.lock();
            try {
                while (count == 0) {
                    notEmpty.await();
                }
                E x = (E) items[takeptr];
                if (++takeptr == items.length) {
                    takeptr = 0;
                }
                --count;
                notFull.signal();
                return x;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 8; i++) {
                    buffer.put(i);
                    System.out.println("生产 " + i + "，缓冲区数量 " + buffer.count);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 8; i++) {
                    sleep(200);   // 消费得慢，生产者放满 3 个后会在 notFull 上等待
                    System.out.println("        消费 " + buffer.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");
        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
    }
}
