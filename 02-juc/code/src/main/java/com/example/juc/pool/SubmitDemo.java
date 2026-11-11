package com.example.juc.pool;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.example.juc.Sleeper.sleep;

/**
 * execute() vs submit()，以及 submit 背后的 FutureTask。
 * <p>
 * AbstractExecutorService#submit 源码：
 * <pre>
 * public &lt;T&gt; Future&lt;T&gt; submit(Callable&lt;T&gt; task) {
 *     RunnableFuture&lt;T&gt; ftask = newTaskFor(task);   // 包装成 FutureTask
 *     execute(ftask);                               // 最终还是调用 execute
 *     return ftask;
 * }
 * </pre>
 * FutureTask#run 会 catch 住任务抛出的所有异常，存进 outcome 字段（setException），不往外抛；
 * 调用 get() 时才包装成 ExecutionException 抛出。所以 submit 的任务出了异常、又没人调 get，异常就“消失”了。
 */
public class SubmitDemo {

    public static void main(String[] args) throws Exception {
        AtomicInteger seq = new AtomicInteger(1);
        ExecutorService pool = Executors.newFixedThreadPool(1, r -> new Thread(r, "worker-" + seq.getAndIncrement()));

        // ===== 实验 1：execute 提交的任务抛异常：打印堆栈，执行它的线程直接死掉，线程池再补一个新线程 =====
        System.out.println("== 实验 1：execute 抛异常 ==");
        pool.execute(() -> System.out.println("任务 A 的线程 = " + Thread.currentThread().getName()));
        pool.execute(() -> {
            throw new IllegalStateException("execute 里的异常（下面这段堆栈是线程的 UncaughtExceptionHandler 打印的）");
        });
        sleep(200);
        pool.execute(() -> System.out.println("任务 C 的线程 = " + Thread.currentThread().getName() + " ← 名字变了，旧线程已经死了"));
        sleep(200);

        // ===== 实验 2：submit 提交的任务抛异常：什么都不打印，get() 时才拿到 =====
        System.out.println("\n== 实验 2：submit 抛异常 ==");
        Future<?> future = pool.submit(() -> {
            throw new IllegalStateException("submit 里的异常");
        });
        sleep(200);
        System.out.println("没有任何堆栈输出，isDone = " + future.isDone());
        try {
            future.get();
        } catch (ExecutionException e) {
            System.out.println("get() 抛出 ExecutionException，原因：" + e.getCause().getMessage());
        }

        // ===== 实验 3：get 一定要带超时，否则任务卡住时调用方也会一直卡住 =====
        System.out.println("\n== 实验 3：get 超时 + cancel ==");
        Future<String> slow = pool.submit(() -> {
            sleep(5000);
            return "slow result";
        });
        try {
            slow.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // cancel(true) 会 interrupt 执行任务的线程；任务里的 sleep 响应中断提前结束
            System.out.println("500ms 没拿到结果，TimeoutException，取消任务：cancel = " + slow.cancel(true));
        }

        // ===== 实验 4：FutureTask 本身就是 Runnable + Future，不用线程池也能用 =====
        System.out.println("\n== 实验 4：直接使用 FutureTask ==");
        FutureTask<Integer> task = new FutureTask<>(() -> {
            sleep(300);
            return 42;
        });
        new Thread(task).start();
        System.out.println("主线程先做别的事……然后 get() 阻塞等待结果 = " + task.get());

        pool.shutdown();
    }
}
