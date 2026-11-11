# JUC 并发编程 Demo

Maven 项目，只依赖 JOL（OpenJDK 官方的对象布局工具，用来看对象头里的锁状态，IDEA 导入时自动下载）。覆盖 [八股.md](../八股.md) 的全部知识点。

代码来源：手撕题 `AlternatePrintSync` 是学习路线 [java.md](../../java.md) 里的原题代码；`ConditionBufferDemo` 是 JDK `Condition` 接口文档里的官方示例；`MiniAqsLock` 按 AQS + `ReentrantLock` 的源码结构精简；线程池、`submit`、CAS 的注释里贴了对应的 JDK 源码片段。

## 运行

1. IDEA → Open → 选择 `02-juc/code` 目录，按 Maven 项目导入，项目 SDK 选 **JDK 17+**。
2. 右键对应类 → Run。并发程序每次输出可能略有不同，注释里写的是“典型结果”。
3. 只有 `ThreadLocalDemo` 需要 VM 参数（反射读 `Thread` 的私有字段）：

   ```
   --add-opens java.base/java.lang=ALL-UNNAMED
   ```

## 学习顺序

| 顺序 | 类 | 内容 |
|---|---|---|
| 1 | `state/ThreadStateDemo` | 6 种状态逐个复现；抢 `ReentrantLock` 失败是 WAITING 不是 BLOCKED；`park/unpark` 的许可证 |
| 2 | `print/AlternatePrintSync` | **手撕题**：`synchronized` + `wait/notifyAll` 交替打印 1~100 |
| 3 | `print/AlternatePrintCondition`、`AlternatePrintSemaphore` | 变形题：三线程 ABC（精准唤醒）、`Semaphore` 解法 |
| 4 | `sync/SyncCounterDemo` | `count++` 竞态、对象锁 vs 类锁、可重入、字节码 `monitorenter` |
| 5 | `sync/LockUpgradeDemo` | 用 JOL 看 Mark Word：无锁 → 轻量级锁 → 重量级锁 |
| 6 | `sync/VolatileDemo` | 可见性（JIT 把循环条件提出去）、不保证原子性、DCL 单例 |
| 7 | `cas/CasDemo` | `AtomicInteger`、手写 CAS 自旋、ABA 与 `AtomicStampedReference`、`LongAdder` |
| 8 | `lock/MiniAqsLock` | 迷你 AQS：`state` + CAS + 等待队列 + `park/unpark`，公平 / 非公平只差一个判断 |
| 9 | `lock/ReentrantLockDemo` | `tryLock` 超时、`lockInterruptibly`、公平锁 vs 非公平锁的获取顺序 |
| 10 | `lock/ConditionBufferDemo` | `Condition` 实现有界缓冲区（`ArrayBlockingQueue` 同款结构） |
| 11 | `pool/ThreadPoolFlowDemo` | 7 大参数、提交流程（核心 → 队列 → 非核心 → 拒绝）、4 种拒绝策略 |
| 12 | `pool/SubmitDemo` | `execute` vs `submit` 的异常处理、`FutureTask`、`get` 超时 |
| 13 | `tool/SyncToolsDemo` | `CountDownLatch`、`Semaphore`、`CyclicBarrier` |
| 14 | `threadlocal/ThreadLocalDemo` | 线程池串号、弱引用 key 被回收但 value 还在、`InheritableThreadLocal` |
| 15 | `deadlock/DeadlockDemo` | 死锁 + `ThreadMXBean` 检测；程序会卡住，正好用来练 `jstack` |

## 动手实验

1. `AlternatePrintSync`：把结束时的 `lock.notifyAll()` 删掉，程序打印完 100 后无法退出（对方线程永远在 wait）。
2. `AlternatePrintCondition`：把 `while (turn != index)` 改成 `if`，多跑几次观察是否会乱序（虚假唤醒 / 被错误唤醒时不再重新检查）。
3. `VolatileDemo`：给 `plainStop` 加上 `volatile`，或者在循环体里加一句 `System.out.print("")`，线程就能停下来了。
4. `ThreadPoolFlowDemo`：把队列换成 `new LinkedBlockingQueue<>()`（无界），再提交 7 个任务：永远只有 2 个线程，最大线程数形同虚设，也永远不会触发拒绝策略 —— 这就是 `Executors.newFixedThreadPool` 可能 OOM 的原因。
5. `SubmitDemo`：在 `submit` 的任务外面自己包一层 `try-catch` 打日志，体会为什么线上任务异常“消失”时要先怀疑 `submit`。
6. `DeadlockDemo` 运行后，在终端执行 `jps -l` 找到 pid，再执行 `jstack <pid>`，在输出末尾找 `Found one Java-level deadlock`。
