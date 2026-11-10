# 参考仓库

这里的开源仓库只在本地阅读，**不上传**（`Java_Learning/.gitignore` 忽略了 `repos/` 下除本文件外的所有内容）。换电脑后按下面的命令重新拉取即可。

## 拉取

```powershell
cd Java_Learning/repos
git clone --depth 1 https://github.com/macrozheng/mall.git
git clone --depth 1 https://github.com/TheYoungChen/hm-dianping.git
```

如果连不上 GitHub，可以临时走本地代理（端口按自己的代理软件改）：

```powershell
git -c http.proxy=http://127.0.0.1:7897 clone --depth 1 https://github.com/macrozheng/mall.git
```

## mall

- 地址：<https://github.com/macrozheng/mall>
- 拉取版本：`dcaa93b`（2026-09-15）
- 技术栈：Spring Boot 3.5 + MyBatis + Spring Security + JWT + Redis + RabbitMQ + Elasticsearch + MongoDB
- 建表脚本：`mall/document/sql/mall.sql`
- Docker 部署：`mall/document/docker/`

### 模块

| 模块 | 说明 |
|---|---|
| `mall-common` | 通用工具、全局异常处理、日志切面 |
| `mall-mbg` | MyBatis Generator 生成的实体和 Mapper |
| `mall-security` | Spring Security + JWT 封装 |
| `mall-admin` | 后台管理系统 |
| `mall-portal` | 前台商城（订单、购物车、RabbitMQ） |
| `mall-search` | Elasticsearch 商品搜索 |
| `mall-demo` | 框架演示代码 |

### 代码导读

| 知识点 | 文件 |
|---|---|
| 全局异常处理 | `mall-common/.../common/exception/GlobalExceptionHandler.java` |
| AOP 接口日志 | `mall-common/.../common/log/WebLogAspect.java` |
| JWT 工具类 | `mall-security/.../security/util/JwtTokenUtil.java` |
| JWT 登录过滤器 | `mall-security/.../security/component/JwtAuthenticationTokenFilter.java` |
| Security 配置 | `mall-security/.../security/config/SecurityConfig.java` |
| AOP 处理 Redis 缓存异常 | `mall-security/.../security/aspect/RedisCacheAspect.java` |
| RabbitMQ 死信队列配置 | `mall-portal/.../portal/config/RabbitMqConfig.java` |
| 订单超时取消（发送 / 消费） | `mall-portal/.../portal/component/CancelOrderSender.java`、`CancelOrderReceiver.java` |

## hm-dianping（黑马点评）

- 地址：<https://github.com/TheYoungChen/hm-dianping>
- 拉取版本：`1b629b9`（2025-06-28）
- 技术栈：Spring Boot 2.3 + MyBatis-Plus + Redis + Redisson
- 建表脚本：`hm-dianping/src/main/resources/db/hmdp.sql`
- 前端（nginx）不在仓库里，需要用黑马课程资料里的 `heimadp-nginx-1.18.0`

> 这个仓库的 README 里写的“12,000 QPS”“JWT 无状态认证”等是简历式描述，和代码不符：登录实际是 **Redis 存 token**，不是 JWT。以代码为准。

### 代码导读

包路径均为 `src/main/java/com/hmdp/`。

| 知识点 | 文件 |
|---|---|
| 登录拦截器 + `ThreadLocal`（Redis 存 token） | `utils/LoginInterceptor.java`、`utils/RefreshTokenInterceptor.java`、`utils/UserHolder.java`、`config/MvcConfig.java` |
| 缓存穿透 / 击穿（互斥锁、逻辑过期） | `utils/CacheClient.java`、`utils/RedisData.java`、`service/impl/ShopServiceImpl.java` |
| 全局唯一 ID | `utils/RedisIdWorker.java` |
| 自研分布式锁（`SET NX EX` + Lua 原子释放） | `utils/ILock.java`、`utils/SimpleRedisLock.java`、`resources/unlock.lua` |
| Redisson 分布式锁 | `config/RedissonConfig.java` |
| 秒杀演进：乐观锁 → Redisson 一人一单 → Lua + 阻塞队列 → Lua + Redis Stream | `service/impl/VoucherOrderServiceImpl.java`（旧版本以注释形式保留在文件里） |
| 秒杀 Lua 脚本（预扣库存 + 判断一人一单 + `XADD` 发消息） | `resources/seckill.lua`；`resources/seckill_old.lua` 是不发消息的旧版 |
| Redis Stream 消费组 + pending-list 重试 | `service/impl/VoucherOrderServiceImpl.java` 中的 `VoucherOrderHandler` |
| 点赞排行榜（ZSet） | `service/impl/BlogServiceImpl.java` |
| 关注 Feed 流（推模式 + ZSet 滚动分页） | `service/impl/BlogServiceImpl.java`、`dto/ScrollResult.java` |
| 共同关注（Set 交集） | `service/impl/FollowServiceImpl.java` |
| 用户签到（BitMap） | `service/impl/UserServiceImpl.java` |
| 全局异常处理 | `config/WebExceptionAdvice.java` |

### 已知问题（学习时注意）

1. **异步秒杀链路是断的**：`VoucherOrderServiceImpl` 第 62 行加载的是 `seckill_old.lua`，这个脚本不会 `XADD` 发消息，所以 Redis 里库存扣了、用户也标记为已购买，但消费者收不到消息，**数据库里不会生成订单**。改成加载 `seckill.lua` 即可。
2. **运行前要先建消费组**：在 Redis 里执行 `XGROUP CREATE stream.orders g1 0 MKSTREAM`，否则消费者线程读 Stream 会一直报错。
3. 没有实现附近商铺（GEO）和 UV 统计（HyperLogLog），需要时自己补。

这两个 bug 本身就是很好的面试素材：Lua 脚本与消费者如何对齐、消费组没初始化会怎样、pending-list 如何保证消息不丢。
