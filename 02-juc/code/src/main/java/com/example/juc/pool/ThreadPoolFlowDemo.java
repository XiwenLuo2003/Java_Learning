package com.example.juc.pool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.juc.Sleeper.sleep;

/**
 * 线程池 7 大参数 + 任务提交流程 + 拒绝策略。
 * <p>
 * ThreadPoolExecutor#execute 源码（JDK 17，去掉了部分细节）：
 * <pre>
 * public void execute(Runnable command) {
 *     int c = ctl.get();                              // ctl 高 3 位存线程池状态，低 29 位存线程数
 *     if (workerCountOf(c) &lt; corePoolSize) {          // ① 线程数 &lt; 核心数：新建核心线程执行
 *         if (addWorker(command, true)) return;
 *         c = ctl.get();
 *     }
 *     if (isRunning(c) &amp;&amp; workQueue.offer(command)) { // ② 核心线程满了：放进队列
 *         ...（再检查一次状态；如果线程数为 0 补一个线程）
 *     }
 *     else if (!addWorker(command, false))           // ③ 队列也满了：新建非核心线程，直到 maximumPoolSize
 *         reject(command);                            // ④ 都满了：拒绝策略
 * }
 * </pre>
 * 注意顺序是“核心线程 → 队列 → 非核心线程”，不是“核心 → 最大 → 队列”。
 */
public class ThreadPoolFlowDemo {

    public static void main(String[] args) throws InterruptedException {
        submitFlow();
        rejectPolicies();
    }

    static ThreadPoolExecutor newPool(String prefix, RejectedExecutionHandler handler) {
        return new ThreadPoolExecutor(
                2,                                  // corePoolSize 核心线程数：常驻，空闲也不回收（除非 allowCoreThreadTimeOut）
                4,                                  // maximumPoolSize 最大线程数：队列满了才会扩到这个数
                10, TimeUnit.SECONDS,               // keepAliveTime + unit：非核心线程空闲多久被回收
                new ArrayBlockingQueue<>(2),        // workQueue：有界队列，容量 2
                new NamedThreadFactory(prefix),     // threadFactory：给线程起名，jstack 排查时一眼看出是哪个池
                handler);                           // handler：拒绝策略
    }

    /** 实验 1：依次提交 7 个耗时 1 秒的任务，看每个任务去了哪里 */
    static void submitFlow() throws InterruptedException {
        System.out.println("== 实验 1：任务提交流程（core=2, max=4, queue=2） ==");
        ThreadPoolExecutor pool = newPool("flow", new ThreadPoolExecutor.AbortPolicy());
        for (int i = 1; i <= 7; i++) {
            int id = i;
            try {
                pool.execute(() -> {
                    System.out.println("    任务 " + id + " 开始执行，线程 = " + Thread.currentThread().getName());
                    sleep(1000);
                });
                System.out.printf("提交任务 %d：线程数 = %d，队列中 = %d%n", id, pool.getPoolSize(), pool.getQueue().size());
            } catch (RejectedExecutionException e) {
                System.out.printf("提交任务 %d：被拒绝（AbortPolicy 抛出 RejectedExecutionException）%n", id);
            }
            sleep(50);
        }
        System.out.println("结论：任务 1、2 → 核心线程；3、4 → 队列；5、6 → 非核心线程；7 → 拒绝。");
        System.out.println("而且 5、6 比 3、4 先执行 —— 后提交的任务反而先跑，因为 3、4 在队列里排队");
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    /** 实验 2：四种内置拒绝策略。每个池先塞满 6 个任务，再提交第 7 个 */
    static void rejectPolicies() throws InterruptedException {
        System.out.println("\n== 实验 2：拒绝策略 ==");
        RejectedExecutionHandler[] handlers = {
                new ThreadPoolExecutor.AbortPolicy(),          // 默认：抛 RejectedExecutionException
                new ThreadPoolExecutor.CallerRunsPolicy(),     // 让提交任务的线程自己执行（天然限流：调用方忙着执行，就没空继续提交）
                new ThreadPoolExecutor.DiscardPolicy(),        // 静默丢弃，什么都不说（危险）
                new ThreadPoolExecutor.DiscardOldestPolicy()   // 丢掉队列里最老的任务，再尝试提交当前任务
        };
        for (RejectedExecutionHandler handler : handlers) {
            String name = handler.getClass().getSimpleName();
            ThreadPoolExecutor pool = newPool(name, handler);
            for (int i = 1; i <= 6; i++) {
                pool.execute(() -> sleep(500));
            }
            try {
                pool.execute(() -> System.out.println("    第 7 个任务执行了，线程 = " + Thread.currentThread().getName()));
                System.out.println(name + "：提交没报错，队列里现在 = " + pool.getQueue().size() + " 个");
            } catch (RejectedExecutionException e) {
                System.out.println(name + "：抛出 RejectedExecutionException");
            }
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        System.out.println("生产上常用自定义拒绝策略：记录日志 + 告警，或者把任务落库 / 发 MQ 稍后重试");
    }

    /** 自定义线程工厂：线程名 = 前缀-序号 */
    static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, prefix + "-" + seq.getAndIncrement());
        }
    }
}
