# Java 后端学习

学习路线见 [java.md](./java.md)（同学整理的大厂日常实习面试路线）。

学习方式：**代码 + 八股结合**。代码不凭空写，而是从开源项目里找到对应实现，精简成能独立运行的 Demo；八股围绕这段代码来整理（项目里为什么这么写、不这么写会出什么问题），背的时候能和代码对上。

## 参考仓库

参考仓库放在 [repos/](./repos/) 下，只在本地阅读，不上传（见 `.gitignore`）。拉取方式和代码导读见 [repos/README.md](./repos/README.md)。

### 1. [macrozheng/mall](https://github.com/macrozheng/mall)：主线项目

完整的电商后端（Spring Boot 3 + MyBatis），技术栈和路线第 3~6 周高度重合：

- **Spring Boot + MyBatis**：IoC/AOP 的实际用法、`@Transactional` 事务、全局异常处理。
- **登录鉴权**：Spring Security + JWT，可以和路线里“`HandlerInterceptor` + JWT 统一鉴权”的考点对照学。
- **Redis**：缓存、验证码。
- **RabbitMQ**：用延迟队列（死信队列 DLX）实现“订单超时自动取消”。
- **MySQL**：表设计规范，订单、库存等 SQL 可以拿来练索引分析和 `EXPLAIN`。
- **Docker**：完整的 Docker 部署文档。

配套教程仓库 [macrozheng/mall-learning](https://github.com/macrozheng/mall-learning) 按技术点拆成了小 Demo，需要时再拉。

### 2. [TheYoungChen/hm-dianping](https://github.com/TheYoungChen/hm-dianping)：Redis 高并发专项

黑马点评，校招里最常见的 Redis 实战项目（Spring Boot 2 + MyBatis-Plus），覆盖路线第 5 周和“防超卖”：

- 缓存穿透（缓存空值）、缓存击穿（互斥锁、逻辑过期）、缓存雪崩。
- 旁路缓存模式：先更新数据库，再删除缓存。
- 秒杀完整演进：乐观锁 `UPDATE ... SET stock = stock - 1 WHERE stock > 0` → Redisson 一人一单 → Lua 预扣库存 + 阻塞队列 → Lua + Redis Stream 异步下单。
- 分布式锁：`SET NX EX` 自研锁 + Lua 原子释放，再到 Redisson。
- Redis 数据结构实战：ZSet 点赞排行和 Feed 滚动分页、Set 共同关注、BitMap 签到。
- 用 `HandlerInterceptor` 做登录拦截，`ThreadLocal` 保存用户信息（顺带讲 `ThreadLocal` 内存泄漏）。

> 这个版本的异步秒杀有个 bug（加载了不发消息的旧版 Lua 脚本），详见 [repos/README.md](./repos/README.md) 的“已知问题”。ZSet 滑动窗口限流需要我们自己扩展。

### 八股参考

[Snailclimb/JavaGuide](https://github.com/Snailclimb/JavaGuide)：用来核对八股答案，不用拉代码。

## 路线模块与代码来源

| 目录 | 路线模块 | 代码来源 |
|---|---|---|
| [01-collection](./01-collection/) | `HashMap`、`ArrayList`、`ConcurrentHashMap` | JDK 源码 + 自写 Demo |
| [02-juc](./02-juc/) | 线程状态、锁、AQS、线程池、`ThreadLocal` | JDK 源码 + 自写 Demo（交替打印等） |
| [03-jvm](./03-jvm/) | 内存区域、GC、OOM 排查 | 自写 OOM / GC Demo + 排查命令 |
| [04-mysql](./04-mysql/) | SQL 练习、索引与 EXPLAIN、MVCC、锁、日志与主从、慢 SQL | 自建练习库（MySQL 8.0）+ hm-dianping 的 `hmdp.sql` 反例 |
| [05-springboot](./05-springboot/) | IoC、AOP、生命周期、自动配置、JWT 鉴权、事务、Spring MVC、MyBatis | 自建 Spring Boot 项目，对照 mall、hm-dianping |
| [06-redis](./06-redis/) | SDS/跳表、ZSet 限流与字段过期、穿透击穿雪崩、双写一致性、分布式锁、秒杀 Stream | 自建 Spring Boot Demo + 对照 hm-dianping（含修 bug） |
| [07-rabbitmq](./07-rabbitmq/) | 交换机、死信延迟队列、Confirm + Outbox、手动 ACK、消费幂等 | mall 订单超时取消 + 自建 Demo（补上 mall 没做的可靠性） |
| [08-network-docker](./08-network-docker/) | TCP、HTTP 状态码、TLS、Namespace/Cgroups、磁盘和 CPU 排查 | JDK 可运行 Demo + mall 的 docker-compose |

## 目录组织

```
Java_Learning/
├── java.md                 # 学习路线
├── README.md               # 本文件
├── .gitignore
├── repos/                  # 开源参考仓库（本地只读，不上传）
│   ├── README.md           # 仓库说明与代码导读（上传）
│   ├── mall/
│   └── hm-dianping/
├── 01-collection/
│   ├── code/               # 精简、注释后的可运行 Demo
│   └── 八股.md             # 结合代码的问答话术
├── 02-juc/
├── 03-jvm/
├── 04-mysql/
├── 05-springboot/
├── 06-redis/
├── 07-rabbitmq/
└── 08-network-docker/
```

## 每个模块的学习流程

1. 在参考仓库（或 JDK 源码）里找到对应实现，读懂。
2. 精简成能单独运行的 Demo，放进 `code/`。
3. 围绕这段代码整理面试问答，写进 `八股.md`。
