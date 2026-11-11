-- =====================================================================
-- 04 三大日志与主从复制：以“观察”为主，原理见 八股.md 第 8 节
-- 需要 root 或有 REPLICATION CLIENT 权限的账号
-- =====================================================================
USE mysql_learning;

-- =====================================================================
-- 实验 1：和持久性相关的几个关键参数
-- =====================================================================
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';
-- 1（默认）：每次提交都把 redo log 刷到磁盘，最安全
-- 0：每秒刷一次，宕机可能丢 1 秒数据；2：提交时写到操作系统缓存，MySQL 崩溃不丢、机器断电可能丢

SHOW VARIABLES LIKE 'sync_binlog';
-- 1（默认）：每次提交都把 binlog 刷盘。和上面的 1 合称“双 1 配置”，保证已提交的事务不丢

SHOW VARIABLES LIKE 'innodb_redo_log_capacity';   -- 8.0.30+：redo log 总大小，循环写
SHOW VARIABLES LIKE 'log_bin';                    -- ON：8.0 默认开启 binlog
SHOW VARIABLES LIKE 'binlog_format';              -- ROW：记录每一行改之前、改之后的值
SHOW VARIABLES LIKE 'transaction_isolation';      -- REPEATABLE-READ

-- =====================================================================
-- 实验 2：亲眼看看 binlog 里记了什么
-- =====================================================================
-- 2.1 切一个新的 binlog 文件，方便只看接下来的操作
FLUSH BINARY LOGS;
SHOW BINARY LOGS;                     -- 记下最后一个文件名，比如 binlog.000012

-- 2.2 执行一次修改
UPDATE account SET balance = balance + 1 WHERE id = 1;

-- 2.3 查看事件（把文件名换成上一步看到的最后一个）
SHOW BINLOG EVENTS IN 'binlog.000012';
-- 能看到：Query(BEGIN) → Table_map(account) → Update_rows → Xid（提交标记）
-- ROW 格式下 Update_rows 是二进制的，想看具体的行数据，在命令行用 mysqlbinlog 解码：
--   mysqlbinlog -vv --base64-output=decode-rows <数据目录>/binlog.000012
-- 会看到类似：### UPDATE mysql_learning.account WHERE @1=1 @3=100.00 SET @1=1 @3=101.00
-- binlog 是“逻辑日志”：记录的是“对哪张表的哪一行做了什么修改”，所以能用来主从复制、数据恢复

SHOW VARIABLES LIKE 'datadir';        -- 数据目录在哪

-- =====================================================================
-- 实验 3：redo log 和 undo log 在哪
-- =====================================================================
-- redo log：8.0.30+ 在数据目录的 #innodb_redo 文件夹里；
-- 在 SHOW ENGINE INNODB STATUS 的 LOG 段能看到 Log sequence number（LSN，redo 写到哪了）
SHOW ENGINE INNODB STATUS;

-- undo log：存在独立的 undo 表空间里，回滚和 MVCC 都靠它
SELECT TABLESPACE_NAME, FILE_NAME FROM information_schema.FILES WHERE FILE_TYPE = 'UNDO LOG';

-- =====================================================================
-- 主从复制（需要两台 MySQL，这里只给出配置和观察命令）
-- =====================================================================
-- 主库 my.cnf：
--   server-id = 1
--   log-bin   = binlog
-- 从库 my.cnf：
--   server-id = 2
--   read_only = ON
-- 主库建复制账号：
--   CREATE USER 'repl'@'%' IDENTIFIED BY '...';
--   GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
-- 从库指向主库并启动（8.0.23+ 语法）：
--   CHANGE REPLICATION SOURCE TO SOURCE_HOST='主库IP', SOURCE_USER='repl', SOURCE_PASSWORD='...',
--       SOURCE_AUTO_POSITION=1;   -- 需要开启 GTID：gtid_mode=ON、enforce_gtid_consistency=ON
--   START REPLICA;
-- 从库上观察：
--   SHOW REPLICA STATUS\G
--   重点看 Replica_IO_Running、Replica_SQL_Running 都是 Yes；Seconds_Behind_Source 是主从延迟秒数
