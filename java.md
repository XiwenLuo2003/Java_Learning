## 一总结了最近找日常的后端

大厂一面（基础面）和二面（项目+架构面）的考察点集中，主要分为四大块：

1. **Java 核心与并发（JUC） & JVM  一面**
   * 高频：`HashMap` 底层原理与扩容机制（为什么 2 倍扩容）、线程池 7 大参数与工作流程、`submit` 原理、线程  6  种状态流转、`synchronized` 与 `ReentrantLock` 区别、JVM 内存区域、OOM 发生场景及线上排查、垃圾回收器（CMS vs G1）。
2. **数据库（MySQL）与中间件（Redis / MQ）  一二面都有**
   * **MySQL**：B+ 树原理（为什么不用红黑树/B树）、聚簇与非聚簇索引（回表、覆盖索引）、联合索引与最左前缀、MVCC 原理、锁机制（`SELECT ... FOR UPDATE`）、慢 SQL 优化、主从复制与三大日志（redo log、undo log、binlog）。
   * **Redis**：常用数据结构及底层（如 String 的 SDS、ZSet 实现滑动窗口/延迟队列）、Hash 字段过期问题、缓存与数据库双写一致性、Lua 脚本保证原子性、分布式锁。
   * **RabbitMQ / Kafka**：技术选型对比（为什么用 RabbitMQ 不用 Kafka）、如何保证消息不丢失（生产端 Confirm、Broker 持久化、消费端手动 ACK）、如何保证消息幂等性（防重复消费）、消息积压处理。
3. **框架（Spring Boot）、网络、Docker 与系统设计**     这部分二面问的多
   * **高频**：Spring Boot 自动配置原理、统一鉴权拦截器（Interceptor/Filter/AOP）、`@Transactional` 事务声明与失效场景、HTTP/HTTPS 握手流程、Docker 隔离原理（Namespace + Cgroups）、线上 CPU/内存/磁盘 95% 告警排查、**高并发系统设计（如分布式限流系统、防超卖系统）**。
4. **手撕代码（算法 + 并发编程 + SQL）（一票否决项，占比 15%）**
   * **高频真题**：LeetCode 经典题（二叉树展开为链表、最长有效括号、合并  M  个有序数组、全排列、接雨水）、**多线程交替打印  1 - 100**、SQL 聚合统计（`GROUP BY`、窗口函数），字节考过代码阅读题，让你读两个并发代码，然后回答代码输出结果。

---

## 二：学习路线天数仅作参考，学的快直接三周速通

时间分配原则：我觉得java语法基础简单看看就行，面试不问语法基础。**50% 学技术栈与做项目 + 30% 背诵核心八股文 + 20% 刷算法题（或者上科技）**。

### 第 1~2 周：Java 核心进阶（集合 + JUC 并发 + JVM）
* **Day 1-4：集合框架源码理解**
  * `ArrayList` 扩容机制、`HashMap`（JDK 1.7 vs 1.8 区别、数组+链表+红黑树、`put` 详细流程、高 16 位异或低 16 位扰动函数、容量为什么是  2  的幂次方）、`ConcurrentHashMap` 并发安全原理。
  * 打开 IDEA，对照源码把 `HashMap` 的 `putVal()` 和 `resize()` 方法逐行看懂，画出一张流程图。
* **Day 5-10：JUC 并发编程（重要）**
  * **必学**：
    * 线程的  6  种状态（`Thread.State`）及切换方法（`wait/notify`、`sleep`、`join`、`park/unpark`）。
    * 锁机制：`synchronized`（锁升级过程、Monitor 原理） vs `ReentrantLock`（AQS 原理、公平锁/非公平锁、`Condition`）、CAS 与 `AtomicInteger`。
    * 线程池：`ThreadPoolExecutor` 的  7  大核心参数、拒绝策略、任务提交流程（核心线程 --阻塞队列 --  最大线程  --  拒绝策略）、`execute()` 与 `submit()`（`FutureTask`）的区别。
    * 线程通信与工具类：`CountDownLatch`、`Semaphore`、`ThreadLocal`（内存泄漏原因）。
* **Day 11-14：JVM 内存与垃圾回收**
  * **必学**：运行时数据区（堆、虚拟机栈、方法区/元空间、程序计数器，哪些线程私有？哪些会 OOM？）、对象创建与内存分配、垃圾回收算法、常见垃圾收集器（ParNew + CMS 的工作流程与缺点、G1 的 Region 设计、ZGC 概念）、线上排查命令（`jps`、`jstat`、`jmap`、`jstack`、Arthas）。

---

### 第 3~4 周：数据库（MySQL）与业务开发基石（Spring Boot）
> **目标**：能够独立写出规范的 CRUD 接口，并能回答关于 MySQL 底层的连环追问。

* **Day 15-21：MySQL 从会用到懂原理**
  * **SQL 实战**：掌握 `INNER JOIN`、`LEFT JOIN`、`RIGHT JOIN` 的区别，熟练写出带 `GROUP BY`、`HAVING`、`COUNT()` 的聚合统计 SQL（小红书、美团一面原题）。
  * **索引底层**（常考）：InnoDB 的 B+ 树结构（算一算  3  层 B+ 树能存多少数据）、聚簇索引（主键索引）与非聚簇索引（二级索引）的区别、什么是**回表**？什么是**覆盖索引**？什么是**最左前缀原则**？
  * **事务与锁**：ACID 特性、四大隔离级别、**MVCC 原理（隐藏字段、Undo Log 版本链、ReadView 可见性算法）**、快照读（普通 `SELECT` 不加锁）与当前读（`SELECT ... FOR UPDATE` 加什么锁？无索引时为什么会锁全表？间隙锁与 Next-Key Lock）。
  * **日志与优化**：Redo Log（保证持久性、两阶段提交）与 Binlog（主从复制、归档）的区别；慢 SQL 优化套路（`EXPLAIN` 看 `type`、`key`、`Extra`，复合索引遵循“等值在前、范围在后”）。
* **Day 22-28：Spring Boot + MyBatis-Plus 极速上手**
  * **必学**：直接学 **Maven + Spring Boot + MyBatis-Plus**。
  * **核心考点**：IoC 与 AOP 原理（动态代理）、Bean 的生命周期与循环依赖、**Spring Boot 自动配置原理（`@SpringBootApplication`  \to  `@EnableAutoConfiguration`  \to  `spring.factories` / `.imports`）**、利用 `HandlerInterceptor`（拦截器）+ JWT 实现统一登录鉴权（小红书一面原题）、`@Transactional` 声明式事务及失效场景。
  * **行动**：用 Spring Boot + MySQL 搭建一个最简单的用户管理系统，实现增删改查、分页查询、全局异常处理、JWT 登录拦截。

---

### 第 5~6 周：高并发中间件（Redis + RabbitMQ）与Linux/Docker基础
> **目标**：掌握处理 高并发、高性能、高可用性

* **Day 29-34：Redis 缓存与分布式应用**
  
  * **数据结构**：5 种基础类型（String、Hash、List、Set、ZSet）的常用命令及底层结构（SDS、跳表 SkipList）。**重点思考：如何用 ZSet 模拟 Hash 内部某个 Field 的过期时间？如何用 ZSet 实现滑动窗口限流？（小红书一二面连环考！）**
  * **高并发场景**：缓存穿透（布隆过滤器/缓存空值）、缓存击穿（互斥锁/逻辑过期）、缓存雪崩；**Redis 与 MySQL 双写一致性（旁路缓存模式：先更新数据库，再删除缓存，为什么？）**。
  * **进阶能力**：使用 `SET NX EX` 或 Redisson 实现分布式锁；使用 **Lua 脚本** 保证多条 Redis 命令的原子性执行。
* **Day 35-39：RabbitMQ 消息队列实战**
  
  * **核心概念**：四大核心角色（Producer、Exchange、Queue、Consumer），交换机类型，死信队列（DLX）与延迟队列。
  * **面试三连问（必背）**：
    
    1. *为什么选 RabbitMQ 而不是 Kafka / RocketMQ？*（RabbitMQ 路由灵活、支持逐条 ACK 确认、死信重试开箱即用，适合任务分发；Kafka 适合高吞吐日志流）。
    
    2. *如何保证消息不丢失？*（生产端 Publisher Confirm + 本地事务 Outbox 表；Broker 队列与消息持久化；消费端关闭自动 ACK，业务执行完手动 ACK）。
    
    3. *如何保证消息幂等性（不重复消费）？*（每条消息带唯一 ID，消费前先插入数据库唯一索引去重表，或用 Redis `SETNX` 拦截）。
    
       malloc
* **Day 40-42：计算机网络、Linux 与 Docker 速成**
  
  * **网络**：TCP 三次握手/四次挥手、HTTP 响应报文结构与常见状态码（200、202 Accepted、401、403、500、502）、**HTTPS 为什么安全及 TLS 握手全流程（非对称加密传对称密钥，对称加密传数据，数字证书防中间人）**。
  * **Docker 与 Linux**：容器与虚拟机的区别、**Docker 底层隔离原理（Linux Namespace 做视图隔离：PID/NET/MNT，Cgroups 做 CPU/内存资源限制）**；Linux 排查命令（`top`、`df -h`、`df -i`、`du`、`lsof`、`netstat`）。
  * **算法同步（第 5-6 周）**：每天 2 道 LeetCode Hot 100（堆/优先队列：**合并 K 个有序链表/数组[美团原题]**、回溯算法：全排列、动态规划基础）。

---

### 第 7~8 周：项目深度包装、AI 加持与全真模拟面试
> **目标**：拒绝“烂大街”的商城增删改查，用学长的项目架构思路打造一份高含金量简历，进行冲刺模拟。

* **Day 43-52：实战项目重构与简历包装（核心！）**
  * 找一个开源的实战项目（如黑马点评、秒杀系统，或者直接复刻学长的 **《分布式任务调度与执行管理平台》**），不要只抄代码，要把以下 **4 个大厂面试官最喜欢的亮点** 融进去：
    1. **异步解耦与接口性能优化**：将耗时长的同步批量请求改造为“快速参数预检 + 落库返回 `202 Accepted` + 后台异步调度”，解决长事务耗尽数据库连接池的问题。
    2. **极端可靠性设计（Outbox + 幂等）**：使用本地消息表（Outbox）+ RabbitMQ 保证任务必定派发；消费端通过“数据库唯一索引去重表 + 进程级状态校验”实现双重幂等。
    3. **并发控制与资源预占**：利用数据库条件原子更新（`UPDATE ... WHERE resource >= need`）或 Redis + Lua 脚本解决高并发下的资源“超卖”问题。
    4. **紧跟时代的 AI / Agent 亮点（加分项）**：现在大厂（美团、快手、小红书）非常关注候选人是否会用 AI Coding 工具（Cursor、Claude Code）以及对 LLM Agent（MCP 协议、上下文压缩、Prompt Cache 计费原理）的理解。可以在个人项目中加入一个简单的 AI 助手模块（调用 DeepSeek/千问 API）。
* **Day 53-60：场景题攻坚与模拟面试**
  * 背熟本文第三部分的《高频真题标准话术》，找学长或同学进行至少 3 次全真语音模拟面试。
  * **算法同步（第 7-8 周）**：二刷做过的错题，重点攻克：**最长有效括号（LeetCode 32，小红书原题）**、接雨水、螺旋矩阵，练习在**记事本（无代码提示）**中手写代码并自己构造 `main` 函数测试用例。

---

## 三：回答话术

背八股文时切忌像背书一样生硬

### 1. Java 与 JUC 篇

#### Q1：平时用过 `HashMap` 吗？讲一下 `put` 方法的逻辑，为什么扩容是乘以  2 ？下标是怎么算的？（快手一面）
* **满分话术**：
  1. **底层结构**：JDK 1.8 里是“数组 + 链表 + 红黑树”。
  2. **`put` 核心流程**：先算哈希定位桶，再处理冲突，最后看要不要扩容。具体来说：
     * 第一步，如果 `table` 数组为空，先调 `resize()` 初始化（默认长度  16 ）。
     * 第二步，计算下标。先拿 `key.hashCode()` 做扰动运算，也就是**高  16  位异或低  16  位**（`(h = key.hashCode()) ^ (h >>> 16)`），让高位特征也参与运算，减少哈希冲突；然后用 **`(n - 1) & hash`** 算出数组下标。
     * 第三步，如果那个桶是空的，直接 `new Node` 放进去；如果不为空，判断头节点 key 是否相同，相同就覆盖；如果是红黑树节点就调树的插入；否则遍历链表尾插，插完后如果**链表长度达到  8  且数组容量  64 **，就把链表转成红黑树（如果容量不到  64  只会先扩容）。
     * 第四步，插入成功后 `size++`，如果超过阈值（容量  \times 0.75 ），就触发 `resize()` 扩容。
  3. **为什么容量是  2  的幂 / 每次乘以  2  扩容**：
     * 一是为了**算下标快**：当容量  n  是  2  的幂时，` n - 1 ` 的二进制全是  1 ，此时按位与 `(n - 1) & hash` 完全等价于取模 `hash % n`，但位运算比取模快得多。
     * 二是为了**摊还扩容成本与高效迁移**：翻倍扩容时，元素在新数组的位置要么在**原位置**，要么在**原位置 + 旧容量**的位置，只需要看 hash 值新增的那个比特位是  0  还是  1  就行，不需要重新计算 hash。

#### Q2：项目里用过线程池吗？有哪几个参数？`synchronized` 和 `ReentrantLock` 有什么区别？（快手一面、小红书一面）
* **满分话术**：
  1. **线程池  7  大参数**：
     * `corePoolSize`（核心线程数，平时常驻的）、`maximumPoolSize`（最大线程数，队列满了才扩到这个数）、`keepAliveTime` + `unit`（非核心线程空闲存活时间）、`workQueue`（阻塞队列，**生产环境一定要用有界队列**防止 OOM）、`threadFactory`（自定义线程名字，方便出问题时用 `jstack` 排查）、`handler`（拒绝策略，如 `CallerRunsPolicy` 让调用者线程执行，或者自定义策略落盘重试）。
     * 如果需要拿执行结果，可以用 `submit()` 提交一个 `Callable` 任务，它会返回一个 `Future` 对象，调 `future.get()` 就能阻塞拿到结果（推荐用带超时时间的 `get`）。
  2. **`synchronized` vs `ReentrantLock`**：
     * `synchronized` 是 JVM 层面内置的关键字，自动加锁和释放锁，代码简单，结合 `wait/notify` 使用，但只有一个等待队列。
     * `ReentrantLock` 是 JUC 包下的显式锁（基于 AQS 实现），必须手动 `lock()` 并在 `finally` 里 `unlock()`。它的优势在于**更灵活**：支持 `tryLock(timeout)` 超时获取锁、支持可中断获取锁（`lockInterruptibly`）、支持公平锁，还能绑定多个 `Condition` 实现精准唤醒（比如阻塞队列里把“生产等待”和“消费等待”分开唤醒）。

---

### 2. MySQL 数据库篇

#### Q3：MySQL 索引底层是什么结构？为什么用 B+ 树？聚簇索引和非聚簇索引有什么区别？联合索引怎么查？（快手一面、美团一面）
* **满分话术**：
  1. **为什么用 B+ 树**：InnoDB 默认用 B+ 树。核心原因是**减少磁盘 IO 次数**和**方便范围查询**。B+ 树的非叶子节点只存键值和页指针，不存整行数据，这样一个  16\text{KB}  的数据页能装下上千个键，扇出极大，树的高度通常只有  3  层左右，意味着查千万级数据最多只需  3  次磁盘 IO。而且所有叶子节点用双向链表串联，做 `>`、`BETWEEN` 范围查询或排序时，找到起点顺着链表扫就行，不用像 B 树那样中序遍历回溯。
  2. **聚簇 vs 非聚簇**：
     * **聚簇索引（主键索引）**：叶子节点直接存放**整行数据**，一张表只有一个。
     * **非聚簇索引（二级索引）**：叶子节点只存**索引列的值 + 主键 ID**。如果用二级索引查整行数据（比如 `SELECT * FROM user WHERE name = '张三'`），先在 `name` 索引树拿到主键 ID，再去主键索引树查整行，这个过程叫**回表**。如果查询列刚好都在二级索引里（比如 `SELECT id, name`），就不需要回表，这叫**覆盖索引**。
  3. **联合索引与优化**：比如建了 `(a, b, c)` 联合索引，底层 B+ 树先按 `a` 排序，`a` 相同再按 `b` 排，再按 `c` 排。查询时必须遵守**最左前缀原则**。在优化多条件组合查询时，建立复合索引的口诀是**“等值查询放前面，范围查询放后面”**（如 `WHERE type = 1 AND create_time > '...'`，索引应该建 `(type, create_time)`）。

#### Q4：讲一下 MVCC 的原理？普通的 `SELECT` 会加锁吗？怎么让它加锁？（快手一面、小红书一面）
* **满分话术**：
  1. **MVCC 核心作用**：多版本并发控制，做到**“读不加锁，读写不冲突”**。
  2. **三大核心组件**：
     * **隐藏字段**：每行记录有隐藏的 `DB_TRX_ID`（最近修改它的事务 ID）和 `DB_ROLL_PTR`（回滚指针）。
     * **Undo Log 版本链**：每次更新数据时不直接覆盖，而是记录一条 undo log，并用回滚指针把历史版本串成一条链表。
     * **ReadView（读视图）**：事务在执行快照读时生成的一个可见性判断快照（包含当前活跃事务列表、最小活跃事务 ID、最大事务 ID 等）。查询时顺着版本链往下比对，只读取在自己快照之前已提交的版本（RC 隔离级别每次 SELECT 都生成新 ReadView，RR 级别复用第一次生成的 ReadView）。
  3. **加锁问题**：普通的 `SELECT * FROM table WHERE ...` 是**快照读，不加任何锁**。如果要加锁（当前读），需要显式写成 `SELECT ... FOR UPDATE`（加排他锁 X 锁）或 `LOCK IN SHARE MODE`（加共享锁 S 锁），常用于扣减库存等防并发修改的场景。

---

### 3. 系统设计与线上排查篇（二面）

#### Q5：【系统设计】假设平台每天有上百万人用，如何设计一个分布式限流系统？支持按用户、IP、接口限流，还要能看最近 1 分钟/1 小时的监控折线图。（小红书二面真题）
* **满分话术**：
  1. **整体架构（两级限流）**：因为单台 Redis 的极限在  10万  QPS 左右，为了防止恶意攻击打垮 Redis，采用 **“网关层本地限流 + Redis 集中式分布式限流”** 两级架构。
  2. **限流算法选择**：
     * **滑动窗口（Redis ZSet）**：适合精细控制。以 `limit:user:{uid}:{api}` 为 Key，请求唯一 ID 为 Member，当前毫秒时间戳为 Score。每次请求用 **Lua 脚本** 原子执行三步：① `ZREMRANGEBYSCORE` 删掉窗口外的旧记录；② `ZCARD` 统计当前窗口内请求数；③ 若未超限则 `ZADD` 写入当前请求并重置过期时间。
     * **令牌桶（Token Bucket）**：如果业务允许一定程度的突发流量（比如用户连续提交几个任务），用令牌桶更合适。为了减轻 Redis 压力，网关实例可以每次向 Redis **批量预取一批令牌**在本地内存扣减，用完再续，或者每  100\text{ms}  批量异步同步一次计数。
  3. **监控折线图实现（采集 --存储 --查询）**：
     * 不能每次请求都写数据库！在网关拦截器里用 **Micrometer** 的 `Counter` 和 `Timer` 在内存里按时间桶预聚合，通过 `/actuator/prometheus` 暴露指标。
     * 用 **Prometheus** 每  15  秒抓取一次落入时序数据库（注意不要把高基数的 `user_id` 当作 Tag，只把接口路径和状态码当 Tag）。
     * 前端或 Grafana 通过 **PromQL 的 Range Query** 查询，看最近  1  分钟用  1\text{s}  步长，看最近  1  小时用  30\text{s}  步长，直接渲染折线图。

#### Q6：【线上排查】收到线上告警，Docker 宿主机磁盘满到 95% / Java 进程 CPU 100%，怎么排查？（小红书一二面真题）
* **满分话术**：
  * **场景 A：磁盘 95% 告警排查**
    1. **先查空间与 Inode**：登机器跑 `df -h` 看哪个挂载分区满了，跑 `df -i` 看是不是小文件太多把 Inode 耗尽了。
    2. **定位大文件**：用 `du -sh /*` 逐层往下找大目录；既然是 Docker 机器，跑 `docker system df` 看镜像、容器、数据卷的占用。最常见的是 `/var/lib/docker/containers/*/*-json.log` 容器标准输出日志没做轮转撑爆了。
    3. **止血与防复发**：不要直接 `rm -rf` 正在被进程写入的日志文件（因为进程还持有文件句柄，空间不会释放，可用 `lsof | grep deleted` 查看），应该用 **`truncate -s 0 xxx.log`** 清空，并用 `docker image prune` 清理无用镜像。事后在 Docker 配置里加上 `log-opts` 限制 `max-size` 和 `max-file` 防止复发。
  * **场景 B：Java 进程 CPU 100% 排查**
    1. `top` 找到占 CPU 最高的 Java 进程 PID。
    2. `top -Hp <PID>` 找到该进程下占用 CPU 最高的线程 TID。
    3. `printf "%x\n" <TID>` 把线程 ID 转成十六进制。
    4. `jstack <PID> | grep -A 30 <十六进制TID>` 查看该线程的堆栈信息，看是业务代码写出了死循环、正则回溯，还是都在 `GC task thread`（说明发生了频繁 Full GC，接着用 `jstat -gcutil` 和 `jmap` 排查内存泄漏）。

---

## 四：手撕

### 必背题 1：双线程交替打印 1~100（小红书二面原题，考并发基本功）
> **面试官要求**：白板（记事本）编写，包含 `main` 函数，要求拷到 IDEA 里能直接编译运行通过，不能有死锁或线程无法退出的问题。

```java
public class AlternatePrint {
    // 1. 定义一把共享锁对象和当前要打印的数字
    private static final Object lock = new Object();
    private static int current = 1;

    public static void main(String[] args) throws InterruptedException {
        // 线程1负责打印奇数 (isOdd = true)，线程2负责打印偶数 (isOdd = false)
        Thread oddThread = new Thread(() -> printNumber(true), "奇数线程");
        Thread evenThread = new Thread(() -> printNumber(false), "偶数线程");

        oddThread.start();
        evenThread.start();

        oddThread.join();
        evenThread.join();
    }

    private static void printNumber(boolean isOdd) {
        while (true) {
            synchronized (lock) {
                // 终止条件：超过100时，必须先唤醒对方再退出，防止对方永久 wait 挂死！
                if (current > 100) {
                    lock.notifyAll();
                    return;
                }

                // 判断当前数字是否轮到自己打印
                if ((current % 2 == 1) == isOdd) {
                    System.out.println(Thread.currentThread().getName() + ": " + current);
                    current++;
                    // 打印完唤醒另一个等待的线程
                    lock.notifyAll();
                } else {
                    // 没轮到自己，释放锁并进入等待状态
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
```

### 必背题 2：二叉树展开为链表（LeetCode 114，快手一面原题）
> **面试官要求**：将二叉树原地（ O(1)  额外空间）按前序遍历顺序展开为只有右子树的单链表，并在本地自己构造 TreeNode 测试用例 debug。

```java
public class FlattenBinaryTree {
    // 树节点定义（面试时经常需要自己手写）
    static class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;
        TreeNode(int val) { this.val = val; }
    }

    // 核心算法：寻找前驱节点法（时间 O(N)，空间 O(1)）
    public static void flatten(TreeNode root) {
        TreeNode curr = root;
        while (curr != null) {
            if (curr.left != null) {
                // 1. 找到左子树的最右下角节点（即前序遍历中左子树的最后一个节点）
                TreeNode predecessor = curr.left;
                while (predecessor.right != null) {
                    predecessor = predecessor.right;
                }
                // 2. 将当前节点的原右子树，接到左子树最右下角节点的后面
                predecessor.right = curr.right;
                // 3. 将左子树整体挪到右边，并将左指针置空（千万别忘置空！）
                curr.right = curr.left;
                curr.left = null;
            }
            // 4. 继续处理下一个节点
            curr = curr.right;
        }
    }

    // 面试自测 main 函数（快手面试官要求现场构造一棵树验证）
    public static void main(String[] args) {
        // 手动写死构造一棵树：
        //     1
        //    / \
        //   2   5
        //  / \   \
        // 3   4   6
        TreeNode n1 = new TreeNode(1);
        TreeNode n2 = new TreeNode(2);
        TreeNode n3 = new TreeNode(3);
        TreeNode n4 = new TreeNode(4);
        TreeNode n5 = new TreeNode(5);
        TreeNode n6 = new TreeNode(6);
        n1.left = n2; n1.right = n5;
        n2.left = n3; n2.right = n4;
        n5.right = n6;

        flatten(n1);

        // 验证输出：顺着 right 指针打印，预期输出 1 -> 2 -> 3 -> 4 -> 5 -> 6
        TreeNode p = n1;
        while (p != null) {
            System.out.print(p.val + (p.right != null ? " -> " : "\n"));
            p = p.right;
        }
    }
}
```
