# JVM 内存与垃圾回收 Demo

Maven 项目，只依赖 JOL（看对象内存布局）。覆盖 [八股.md](../八股.md) 的全部知识点。

JVM 的知识点主要靠**改参数、看现象**来学：每个类的注释第一段都写了要加的 VM 参数（Run → Edit Configurations → Modify options → Add VM options），以及预期看到什么。

> 本模块所有排查命令（`jps`、`jstat`、`jmap`、`jstack`、`jcmd`）都在 JDK 的 `bin` 目录里，装了 JDK 就有，不需要额外安装。在 IDEA 底部的 Terminal 里执行即可；如果提示找不到命令，用 JDK 的完整路径执行（IDEA 的 File → Project Structure → SDKs 里能看到 JDK 路径）。

## 学习顺序

| 顺序 | 类 | 内容 | VM 参数 |
|---|---|---|---|
| 1 | `memory/ObjectLayoutDemo` | 对象头（Mark Word + 类型指针）、字段重排、对齐填充、指针压缩 | 无（可选 `-XX:-UseCompressedOops` 对比） |
| 2 | `oom/HeapOomDemo` | 堆溢出 + 自动导出堆快照 | `-Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=heapdump.hprof` |
| 3 | `oom/StackOverflowDemo` | 栈溢出，栈帧大小对深度的影响 | 先不加，再加 `-Xss256k` 对比 |
| 4 | `oom/MetaspaceOomDemo` | 元空间溢出：同一个类被不同加载器反复加载 | `-XX:MaxMetaspaceSize=32m` |
| 5 | `oom/DirectMemoryOomDemo` | 直接内存溢出 | `-XX:MaxDirectMemorySize=10m` |
| 6 | `gc/ReachabilityDemo` | 可达性分析：循环引用也能回收、GC Roots | 可选 `-Xlog:gc` |
| 7 | `gc/ReferenceDemo` | 强、软、弱、虚四种引用的回收时机 | `-Xmx20m -Xlog:gc` |
| 8 | `gc/AllocationDemo` | Eden 分配与 Minor GC、大对象直接进老年代、年龄晋升 | `-XX:+UseSerialGC -Xms20m -Xmx20m -Xmn10m -XX:SurvivorRatio=8 -Xlog:gc*`，另见类注释 |
| 9 | `classload/ClassInitDemo` | 类初始化时机、被动引用、静态块与实例块的执行顺序 | 无 |
| 10 | `classload/ClassLoaderDemo` | 类加载器层级、双亲委派、打破双亲委派、类的唯一性 | 无 |
| 11 | `troubleshoot/TroubleshootTarget` | **排查练习靶子**：CPU 飙高 + 内存泄漏 + 死锁 | `-Xms256m -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=troubleshoot.hprof` |

## GC 日志怎么读

`AllocationDemo minor` 的日志大致是这样（数字会有出入）：

```
[gc,start    ] GC(0) Pause Young (Allocation Failure)          ← Eden 放不下 4MB，触发 Minor GC
[gc,heap     ] GC(0) DefNew: 6627K(9216K)->512K(9216K) ...      ← 新生代 6.6MB → 0.5MB
[gc,heap     ] GC(0) Tenured: 0K(10240K)->6144K(10240K)         ← 老年代 0 → 6MB（三个 2MB 数组被担保晋升）
[gc          ] GC(0) Pause Young (Allocation Failure) 7M->6M(19M) 3.123ms
...
[gc,heap,exit] def new generation   total 9216K, used 4444K     ← 退出时 Eden 里是 4MB 的 a4
[gc,heap,exit] tenured generation   total 10240K, used 6144K
```

去掉 `-XX:+UseSerialGC` 就是 JDK 17 默认的 G1，日志会变成 `Pause Young (Normal) (G1 Evacuation Pause)`，堆被划分成很多 Region。

## 线上排查实操（用 TroubleshootTarget）

启动 `TroubleshootTarget`（不填参数，三种问题同时出现），然后在 Terminal 里：

**第 1 步：找到进程**

```
jps -l
```

输出里 `com.example.jvm.troubleshoot.TroubleshootTarget` 前面的数字就是 pid（程序启动时也打印了）。

**第 2 步：CPU 飙高 —— 找到最耗 CPU 的线程**

Linux 上的标准流程是 `top` → `top -Hp <pid>` 找线程 → `printf "%x\n" <tid>` 转十六进制 → `jstack <pid> | grep -A 30 <十六进制>`。Windows 上没有 `top -Hp`，直接看 jstack 里 RUNNABLE 状态、并且两次 jstack 都停在同一段业务代码的线程：

```
jstack <pid>
```

找到 `"hot-loop"` 线程，状态 `RUNNABLE`，栈顶是 `TroubleshootTarget.lambda$startCpuHog$0`，就是死循环的位置。（Linux 上 `top -Hp` 看到的 tid 转成十六进制，就是 jstack 里的 `nid=0x...`）

**第 3 步：死锁**

同一份 jstack 输出拉到最后，有 `Found one Java-level deadlock:`，写明了 `order-service` 和 `stock-service` 各自持有哪把锁、在等哪把锁，还给出了代码行号。

**第 4 步：内存泄漏 —— 先看 GC 趋势**

```
jstat -gcutil <pid> 1000
```

每秒打印一行：`O`（老年代使用率）持续上涨、`FGC`（Full GC 次数）不断增加、但 `O` 降不下来 —— 典型的内存泄漏特征（GC 也回收不掉）。

**第 5 步：内存泄漏 —— 看是谁占的内存**

```
jmap -histo:live <pid>
```

按占用排序的类直方图，排第一的是 `[B`（byte 数组）。`:live` 会先触发一次 Full GC，只统计存活对象，线上慎用（会停顿）。

**第 6 步：导出堆快照，找到引用链**

```
jmap -dump:live,format=b,file=manual.hprof <pid>
```

或者等它 OOM 自动生成 `troubleshoot.hprof`。用 IDEA Ultimate、VisualVM 或 Eclipse MAT 打开，在支配树里能看到 `TroubleshootTarget.LEAKY_CACHE` 这个 ArrayList 拉着两百多个 `byte[]`。

**第 7 步：其他常用命令**

```
jcmd <pid> VM.flags           实际生效的 JVM 参数
jcmd <pid> GC.heap_info       堆各区域使用情况
jcmd <pid> Thread.print       等同于 jstack
jinfo -flags <pid>            查看 JVM 参数
```

**Arthas**（阿里开源的在线诊断工具，需要自己从官网下载 `arthas-boot.jar`，用 `java -jar arthas-boot.jar` 选择进程 attach）：`dashboard` 实时看线程 CPU 和内存，`thread -n 3` 直接列出最耗 CPU 的 3 个线程及堆栈，`thread -b` 找出阻塞其他线程的那个线程，`heapdump` 导出堆，`watch` / `trace` 看方法的入参返回值和耗时。是否安装由你决定，上面的 JDK 命令已经能完成全部排查。

练完记得在 IDEA 里点停止按钮。
