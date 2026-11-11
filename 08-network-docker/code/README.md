# 计算机网络、Linux 与 Docker

网络部分是三个可以直接运行的 `main`，不监听端口以外的任何东西（HTTP 那个只连自己的 8083）。Docker 和 Linux 排查不在本机执行：命令和 mall 的 compose 写在 [八股.md](../八股.md) 里，需要时自己在 Linux 机器上跑。

CPU 100% 的 Java 靶子不重复造，用 [03-jvm 的 TroubleshootTarget](../../03-jvm/code/src/main/java/com/example/jvm/troubleshoot/TroubleshootTarget.java)。

## 运行

IDEA → Open → `08-network-docker/code`。JDK 17+。没有第三方依赖。

| 类 | 看什么 |
|---|---|
| `net/TcpHandshakeDemo` | 三次握手、四次挥手、TIME_WAIT、SYN Flood |
| `net/HttpStatusServer` | 响应报文长什么样；200 / 202 / 401 / 403 / 500 / 502。跑完自己退出 |
| `net/TlsHandshakeDemo` | 证书验签、RSA 搬不动大数据、AES-GCM 加密和防篡改 |

## Docker / Linux 材料

| 文件 | 内容 |
|---|---|
| [docker/daemon-log.json](./docker/daemon-log.json) | 容器日志轮转。磁盘告警里最常见的复发点 |
| `repos/mall/document/docker/docker-compose-env.yml` | mall 怎么起 MySQL、Redis、RabbitMQ。对照看端口、数据卷、镜像 |
| [八股.md](../八股.md) 第 5、6 节 | Namespace / Cgroups，以及磁盘 95%、CPU 100% 的排查顺序 |
