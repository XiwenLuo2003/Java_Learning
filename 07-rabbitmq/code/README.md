# RabbitMQ 学习项目

覆盖 [八股.md](../八股.md) 的交换机、延迟队列、消息不丢和消费幂等。延迟队列的接法对照 mall 的 `RabbitMqConfig` / `CancelOrderSender`，并补上 mall 没有的三件事：Publisher Confirm、本地消息表、手动 ACK。

## 环境（自己准备，本目录不替你装）

1. **JDK 17+**
2. **RabbitMQ**，监听 `127.0.0.1:5672`，账号 `guest` / `guest`。管理台 <http://127.0.0.1:15672>。
   - 改地址：[`application.yml`](./src/main/resources/application.yml)
   - mall 的部署方式见 `repos/mall/document/docker/docker-compose-env.yml` 的 `rabbitmq` 服务（镜像 `rabbitmq:3.9.11-management`，端口 5672 和 15672）
3. IDEA → Open → 选择 `07-rabbitmq/code`。数据库是内嵌 H2，不用装 MySQL。

没启动 RabbitMQ 就运行 `MqDemoApplication`，监听容器会一直连不上，应用起不来。

## 运行

运行 `MqDemoApplication`，用 [requests.http](./requests.http) 发请求（端口 **8082**）。

## 学习顺序

| 课 | 知识点 | 怎么看 |
|---|---|---|
| 1 | direct / fanout / topic | requests 第 1 组，看每个队列的 `received` |
| 2 | TTL + 死信做延迟队列，以及队头阻塞 | 第 2 组。`head-block` 大约要 5 秒 |
| 3 | Publisher Confirm 和 mandatory return | 第 3 组 |
| 4 | Outbox、手动 ACK、唯一索引去重 | 第 4 组，按顺序点 |
| 5 | nack 重新入队 vs 进死信队列 | 第 5 组 |

## 目录

```
src/main/java/com/example/mq/
├── config/RabbitConfig.java          交换机、队列、死信参数
├── demo/MqDemoController.java        全部实验入口
└── reliable/
    ├── OrderService.java             下单事务 + 消费事务
    ├── OutboxPublisher.java          扫本地消息表，confirm 成功才改状态
    ├── OrderCreatedListener.java     手动 ACK
    ├── AckDemoListener.java          nack / 死信
    └── SetnxIdempotent.java          Redis SETNX 去重的写法（本项目不连 Redis）
```

## 和 mall 的差别（面试时直接说）

1. mall 用消息上的 `expiration` 做延迟，队列只检查**队头**。先发的长 TTL 会堵住后发的短 TTL。实验 3 把这个现象固定下来。准时延迟要用 `rabbitmq-delayed-message-exchange` 插件。
2. mall 的 `CancelOrderReceiver` 是自动 ACK。方法抛异常时消息已经没了，订单取消会丢。这里改成业务事务提交之后才 `basicAck`。
3. mall 没有本地消息表。“订单写入成功、发消息之前进程挂了”会丢消息。这里订单和 `tb_outbox` 同一个事务，发布器确认之后才把 status 改成 1。
