# 集合框架 Demo

不依赖任何第三方库的 Maven 项目，覆盖 [八股.md](../八股.md) 的全部知识点。代码以 JDK 17 源码为蓝本：`MiniHashMap` 是 `HashMap` 的逐行精简版，`Jdk7ResizeLoopDemo` 用的是 JDK 7 `HashMap#transfer` 的原始逻辑，`MiniConcurrentHashMap` 是 `ConcurrentHashMap#putVal` 的精简版；其余 Demo 用反射读取真实集合类的内部字段，验证源码里读到的结论。

## 运行

1. IDEA → Open → 选择 `01-collection/code` 目录，按 Maven 项目导入，项目 SDK 选 **JDK 17+**。
2. 右键对应类 → Run。
3. `ArrayListDemo`、`HashMapDemo` 要反射读 `java.util` 的私有字段，需要加 VM 参数（Run → Edit Configurations → Modify options → Add VM options）：

   ```
   --add-opens java.base/java.util=ALL-UNNAMED
   ```

## 学习顺序

| 顺序 | 类 | 内容 | VM 参数 |
|---|---|---|---|
| 1 | `arraylist/ArrayListDemo` | 扩容序列 0→10→15→22、`new ArrayList<>(0)` 的不同、fail-fast、`Arrays.asList` 坑 | 需要 |
| 2 | `hashmap/MiniHashMap` | **对照 IDEA 里的 JDK 源码读** `hash`、`putVal`、`resize`、`treeifyBin` | — |
| 3 | `hashmap/HashMapDemo` | 扰动函数、`(n-1)&hash`、`tableSizeFor`、扩容 lo/hi 拆分、转红黑树条件、`equals`/`hashCode` | 需要 |
| 4 | `hashmap/Jdk7ResizeLoopDemo` | JDK 7 头插法并发扩容成环 + 丢数据（单线程模拟两个线程的交错执行，结果稳定） | — |
| 5 | `concurrent/MiniConcurrentHashMap` | 空桶 CAS、非空桶锁头节点、`LongAdder` 式计数 | — |
| 6 | `concurrent/ConcurrentHashMapDemo` | HashMap 并发丢数据、复合操作陷阱、`merge`、不允许 null、弱一致迭代 | — |

## 读源码的方法

1. IDEA 里双击 `Shift` 搜索 `HashMap`，打开 JDK 源码；`Ctrl+F12` 列出方法，找到 `putVal`。
2. 左右分屏：左边 JDK 源码，右边 [MiniHashMap](src/main/java/com/example/collection/hashmap/MiniHashMap.java)，变量名完全一样，注释在右边。
3. 在 `HashMapDemo` 的 `resizeSplit()` 里打断点，Step Into 到 `MiniHashMap#resize`，看 `loHead`、`hiHead` 怎么一步步串起来。
4. 路线要求“画一张流程图”：八股里已经给了 `putVal` 的流程图，可以对照自己画一遍。
