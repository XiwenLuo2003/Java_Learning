# MySQL 实验脚本

MySQL 这部分的“代码”就是 SQL：每个脚本对应 [八股.md](../八股.md) 的一到两节，脚本里写好了预期结果，执行后对照着看。

和 Java 的结合（MyBatis 的 `#{}` / `${}`、动态 SQL、一对多映射、慢 SQL 插件）在 [05-springboot](../../05-springboot/code/README.md) 的第 12、13 课。

## 环境

- **MySQL 8.0.18 及以上**（用到窗口函数、递归 CTE、`EXPLAIN ANALYZE`、`performance_schema.data_locks`）。
- 客户端任选：mysql 命令行、DataGrip、Navicat、IDEA 自带的 Database 工具窗口。
- 用 root 或有足够权限的账号执行（04、05 里有查看 binlog、修改慢查询配置的语句）。

## 执行顺序

| 脚本 | 内容 | 对应八股 |
|---|---|---|
| [00_init.sql](./00_init.sql) | 建库 `mysql_learning`、建表、造 10 万行测试数据。**其他脚本执行前先完整执行它**，可重复执行 | — |
| [01_sql_practice.sql](./01_sql_practice.sql) | 10 道手写 SQL 题：JOIN、GROUP BY / HAVING、COUNT、窗口函数、连续 N 天 | 第 1 节 |
| [02_index.sql](./02_index.sql) | B+ 树容量、回表、覆盖索引、最左前缀、key_len、索引失效、ORDER BY 走索引 | 第 2、3、4 节 |
| [03_transaction_lock.sql](./03_transaction_lock.sql) | **两个会话交替执行**：脏读、不可重复读、快照读 vs 当前读、ReadView 生成时机、间隙锁、Next-Key Lock、无索引锁全表、死锁 | 第 5、6、7 节 |
| [04_log_replication.sql](./04_log_replication.sql) | 双 1 配置、查看 binlog 内容、redo / undo 位置、主从复制配置 | 第 8 节 |
| [05_slow_sql.sql](./05_slow_sql.sql) | 慢查询日志、深分页的两种优化、“等值在前范围在后”对比、COUNT 优化、优化清单 | 第 9 节 |

## 注意

- `EXPLAIN` 的 `rows` 是估算值，不同机器会有差别；`type`、`key`、`key_len`、`Extra` 应该和注释里的预期一致。
- `03_transaction_lock.sql` 每个实验开始前先执行文件开头的“重置数据”。
- `05_slow_sql.sql` 会修改全局的慢查询配置，做完实验记得按脚本里的语句恢复。
