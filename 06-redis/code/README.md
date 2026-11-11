# Redis 学习项目

一个 Spring Boot 项目覆盖 [八股.md](../八股.md) 里的全部知识点。数据结构底层（SDS、跳表、布隆过滤器、近似 LRU）是纯 Java，直接跑 `main`；高并发场景、分布式锁、秒杀连真实 Redis。

代码对照开源项目 [repos/hm-dianping](../../repos/hm-dianping)（黑马点评），并修正了其中两个已知问题（逻辑过期提前释放锁、秒杀加载了不发消息的旧 Lua）。

## 环境（自己准备，本目录不替你装）

1. **JDK 17+**
2. **Redis 6+**（要有 Stream；建议 7.x）。本机启动后监听 `127.0.0.1:6379`，无密码。
   - Windows 可用 [Memurai](https://www.memurai.com/)、WSL 里的 Redis，或 Docker：`docker run -d -p 6379:6379 redis:7`
   - 改地址：编辑 [`application.yml`](./src/main/resources/application.yml)
3. IDEA → Open → 选择 `06-redis/code`，按 Maven 项目导入。H2 是内嵌库，不用装 MySQL。

## 运行

1. 确认 Redis 已启动：`redis-cli ping` 应返回 `PONG`。
2. 原理课（第 0 课）：右键运行 `principle` 包下各类的 `main`，**不需要 Redis**。
3. 其他课：运行 `RedisDemoApplication`，用 [requests.http](./requests.http) 逐个发请求（端口 **8081**）。

## 学习顺序

| 课 | 知识点 | 代码 | 怎么看效果 |
|---|---|---|---|
| 0 | SDS、跳表、布隆过滤器、近似 LRU | `principle/MiniSds`、`MiniSkipList`、`MiniBloomFilter`、`ApproxLruDemo` | 直接跑 main |
| 1 | 5 种基础类型 + BitMap + 全局 ID | `datatype/DatatypeDemoController` | requests 第 1 组 |
| 2 | ZSet 滑动窗口限流、Hash 字段过期、延迟队列 | `zset/*`、`resources/lua/*.lua` | requests 第 2 组；对照 `java.md` 第 151 行 |
| 3 | 缓存穿透 / 击穿 / 雪崩 | `cache/CacheClient`、`CacheDemoController` | requests 第 3 组，看 `dbQueries` |
| 4 | 双写一致性（旁路缓存） | `consistency/*` | requests 第 4 组，看 `consistent` 和 timeline |
| 5 | 分布式锁：SET NX EX → Lua → Redisson | `lock/*`、`lua/unlock.lua` | requests 第 5 组 |
| 6 | 秒杀：Lua 预扣 + Stream 消费组 + pending-list | `seckill/*`、`lua/seckill.lua` | requests 第 6 组 |

## 目录结构

```
src/main/java/com/example/redis/
├── RedisDemoApplication.java
├── common/          Concurrent、Timeline、RedisIdWorker
├── config/          RedissonConfig
├── principle/       纯 Java 原理（无 Redis）
├── datatype/        5 种类型命令演示
├── zset/            滑动窗口、字段过期、延迟队列
├── cache/           CacheClient + 穿透/击穿/雪崩实验
├── consistency/     双写一致性时序实验
├── lock/            自研锁 + Redisson 对比
└── seckill/         异步秒杀

src/main/resources/
├── application.yml
├── schema.sql / data.sql
└── lua/             unlock / sliding_window / hash_field_expire / delay_queue_poll / seckill / seckill_old
```

## 动手实验

1. `MiniSds`：把 `SDS_MAX_PREALLOC` 改成 `1`，看追加时每次只多 1 字节、扩容次数暴增。
2. `CacheClient#queryWithLogicalExpire`：改回黑马点评那种“提交任务后立刻 unlock”，再跑 `/cache/breakdown?mode=logical`，`dbQueries` 会大于 1。
3. `/consistency/race?strategy=updateThenDeleteRare`：把 R 的卡顿从 500ms 改成 50ms，不一致现象会消失——说明这个问题依赖时序，生产上要用延迟双删或加锁。
4. `/lock/watchdog?leaseSeconds=0`：看 TTL 每次被续回约 30000；改成 `leaseSeconds=10` 则到期后 `remainTimeToLive=-2`。
5. `/seckill/race?useOldScript=true`：亲眼看 Redis 库存扣了、数据库订单为 0；对照黑马点评 `VoucherOrderServiceImpl` 第 62 行加载的 `seckill_old.lua`。

## 和黑马点评的对应关系

| 本项目 | 黑马点评 |
|---|---|
| `CacheClient` | `utils/CacheClient`（修正了提前释放锁） |
| `SimpleRedisLock` + `unlock.lua` | `utils/SimpleRedisLock` + `unlock.lua` |
| `RedisIdWorker` | `utils/RedisIdWorker` |
| `SeckillService` + `seckill.lua` | `VoucherOrderServiceImpl` + `seckill.lua`（仓库当前误加载了 `seckill_old.lua`） |
| `consistency` 的 `txDeleteInside` | `ShopServiceImpl#update`（事务内删缓存） |
| `SlidingWindowRateLimiter` | 路线要求自己补写（`java.md` 限流方案） |
