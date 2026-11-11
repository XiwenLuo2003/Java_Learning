-- =====================================================================
-- 03 事务、MVCC 与锁：两个会话交替执行的实验
--
-- 准备：在客户端里开两个独立的查询窗口（两个连接），分别叫“会话 A”和“会话 B”。
--   每个窗口先执行：USE mysql_learning;
-- 按 [步骤 1] [步骤 2] …… 的顺序，在标注的会话里执行对应语句，观察结果。
-- 每个实验开始前先执行一次“重置数据”。
--
-- 有些步骤会被锁阻塞（一直转圈），这是预期现象。会话 B 先执行下面这句，最多等 10 秒就报锁超时，不用干等：
--   SET SESSION innodb_lock_wait_timeout = 10;
-- 被阻塞的语句报 ERROR 1205 Lock wait timeout 之后，再继续执行同一会话的下一句。
-- 最直观的方式是开两个 mysql 命令行窗口；用图形客户端时，要确认两个查询窗口是两个独立的连接。
-- =====================================================================
USE mysql_learning;

-- 重置数据（任意会话执行）
UPDATE account SET balance = 100 WHERE id IN (1, 2);
DELETE FROM lock_demo;
INSERT INTO lock_demo VALUES (5, 5, 5), (10, 10, 10), (15, 15, 15), (20, 20, 20);

-- 查看当前锁（任意会话执行，加锁的实验里随时可以看）：
--   LOCK_TYPE：TABLE 表锁 / RECORD 行锁
--   LOCK_MODE：X 表示 Next-Key Lock（记录 + 前面的间隙）；X,REC_NOT_GAP 只锁记录；X,GAP 只锁间隙；
--              IX 意向排他锁（表级）
--   LOCK_DATA：锁住的是哪条索引记录，supremum pseudo-record 表示“正无穷”
SELECT ENGINE_TRANSACTION_ID AS trx_id, OBJECT_NAME AS table_name, INDEX_NAME,
       LOCK_TYPE, LOCK_MODE, LOCK_STATUS, LOCK_DATA
FROM performance_schema.data_locks;

-- 查看正在运行的事务和锁等待：
SELECT trx_id, trx_state, trx_started, trx_isolation_level, trx_query FROM information_schema.innodb_trx;


-- =====================================================================
-- 实验 1：脏读（READ UNCOMMITTED）
-- =====================================================================
-- [步骤 1][会话 A]
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
BEGIN;
SELECT balance FROM account WHERE id = 1;       -- 100

-- [步骤 2][会话 B]
BEGIN;
UPDATE account SET balance = 200 WHERE id = 1;  -- 不提交

-- [步骤 3][会话 A]
SELECT balance FROM account WHERE id = 1;       -- 200：读到了 B 没提交的数据，这就是脏读

-- [步骤 4][会话 B]
ROLLBACK;

-- [步骤 5][会话 A]
SELECT balance FROM account WHERE id = 1;       -- 100：刚才读到的 200 是根本不存在的数据
COMMIT;


-- =====================================================================
-- 实验 2：不可重复读（READ COMMITTED） vs 可重复读（REPEATABLE READ）
-- =====================================================================
-- [步骤 1][会话 A]
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
BEGIN;
SELECT balance FROM account WHERE id = 1;       -- 100

-- [步骤 2][会话 B]
UPDATE account SET balance = 200 WHERE id = 1;  -- 自动提交

-- [步骤 3][会话 A]
SELECT balance FROM account WHERE id = 1;       -- 200：同一个事务里两次读结果不同，这就是不可重复读
COMMIT;
-- 原因：RC 级别下，每次 SELECT 都会生成一个新的 ReadView

-- 重置数据后，把会话 A 换成 REPEATABLE READ（MySQL 默认级别）重做一遍：
-- [步骤 1][会话 A]
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN;
SELECT balance FROM account WHERE id = 1;       -- 100

-- [步骤 2][会话 B]
UPDATE account SET balance = 200 WHERE id = 1;

-- [步骤 3][会话 A]
SELECT balance FROM account WHERE id = 1;       -- 还是 100：RR 级别复用第一次 SELECT 生成的 ReadView
COMMIT;
SELECT balance FROM account WHERE id = 1;       -- 200：事务结束后再读，才看到最新值


-- =====================================================================
-- 实验 3：快照读 vs 当前读（RR 级别下 MVCC 最重要的实验）
-- =====================================================================
-- [步骤 1][会话 A]
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN;
SELECT balance FROM account WHERE id = 1;                  -- 100（快照读，生成 ReadView）

-- [步骤 2][会话 B]
UPDATE account SET balance = 200 WHERE id = 1;            -- 自动提交

-- [步骤 3][会话 A]
SELECT balance FROM account WHERE id = 1;                  -- 100：快照读，读的是 ReadView 能看到的历史版本（undo log）
SELECT balance FROM account WHERE id = 1 FOR UPDATE;       -- 200：当前读，读最新已提交版本，并加 X 锁
UPDATE account SET balance = balance + 10 WHERE id = 1;    -- UPDATE 也是当前读：基于 200 加 10，不是基于 100
SELECT balance FROM account WHERE id = 1;                  -- 210：自己修改过的行，对自己可见
COMMIT;
-- 结论：如果业务里先普通 SELECT 拿到余额，在 Java 里计算后再 UPDATE 写回，就会覆盖掉别人的修改（丢失更新）。
-- 正确做法：SELECT ... FOR UPDATE 加锁再算；或者直接 UPDATE ... SET balance = balance - ? WHERE balance >= ?


-- =====================================================================
-- 实验 4：ReadView 是在第一次 SELECT 时生成的，不是 BEGIN 时
-- =====================================================================
-- [步骤 1][会话 A]
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN;                                           -- 只 BEGIN，不查询

-- [步骤 2][会话 B]
UPDATE account SET balance = 300 WHERE id = 1;

-- [步骤 3][会话 A]
SELECT balance FROM account WHERE id = 1;        -- 300：ReadView 在这一刻才生成，所以能看到 B 的修改
COMMIT;
-- 想在开启事务时立刻生成快照，用：START TRANSACTION WITH CONSISTENT SNAPSHOT;（mysqldump 备份就是这么做的）


-- =====================================================================
-- 实验 5：间隙锁 —— 主键等值查询一个不存在的值
-- =====================================================================
-- 重置数据。lock_demo 的 id 是 5、10、15、20
-- [步骤 1][会话 A]
BEGIN;
SELECT * FROM lock_demo WHERE id = 7 FOR UPDATE;   -- 空结果，但锁住了间隙 (5, 10)

-- [步骤 2][会话 B]
SET SESSION innodb_lock_wait_timeout = 10;
INSERT INTO lock_demo VALUES (8, 8, 8);             -- 阻塞：8 落在间隙 (5, 10) 里
INSERT INTO lock_demo VALUES (12, 12, 12);          -- 立即成功：间隙 (10, 15) 没被锁

-- 查看锁：会看到 lock_demo 的 PRIMARY 上有一条 LOCK_MODE = X,GAP，LOCK_DATA = 10
-- [步骤 3][会话 A]
ROLLBACK;
-- 为什么要锁间隙？防止别的事务在这个范围插入新行，避免当前读出现幻读


-- =====================================================================
-- 实验 6：Next-Key Lock —— 普通索引上的范围当前读
-- =====================================================================
-- 重置数据
-- [步骤 1][会话 A]
BEGIN;
-- 表只有 4 行，优化器可能嫌走索引麻烦直接全表扫描，所以用 FORCE INDEX 保证走 idx_k
SELECT * FROM lock_demo FORCE INDEX (idx_k) WHERE k BETWEEN 10 AND 15 FOR UPDATE;   -- 返回 k = 10、15 两行

-- [步骤 2][会话 B]
SET SESSION innodb_lock_wait_timeout = 10;
INSERT INTO lock_demo VALUES (12, 12, 12);   -- 阻塞：k = 12 在 (10, 15] 里
INSERT INTO lock_demo VALUES (7, 7, 7);      -- 也阻塞！k = 10 上的 Next-Key Lock 锁的是 (5, 10]，7 在这个间隙里
INSERT INTO lock_demo VALUES (25, 25, 25);   -- 立即成功：在锁住的范围之外
UPDATE lock_demo SET v = 99 WHERE id = 5;    -- 立即成功：id = 5 这行记录本身没被锁

-- 查看锁：idx_k 上有 LOCK_MODE = X 的记录（Next-Key Lock = 记录锁 + 前面的间隙锁），
-- 同时 PRIMARY 上有 X,REC_NOT_GAP（回表时给主键记录加的记录锁）
-- [步骤 3][会话 A]
ROLLBACK;
-- 结论：RR 级别下，当前读加锁的基本单位是 Next-Key Lock（左开右闭区间），它和 MVCC 一起解决了幻读


-- =====================================================================
-- 实验 7：没有索引的列做当前读，会锁住全表
-- =====================================================================
-- 重置数据
-- [步骤 1][会话 A]
BEGIN;
SELECT * FROM lock_demo WHERE v = 10 FOR UPDATE;   -- 只返回 1 行，但 v 没有索引

-- [步骤 2][会话 B]
SET SESSION innodb_lock_wait_timeout = 10;
UPDATE lock_demo SET k = 99 WHERE id = 20;         -- 阻塞！和 v = 10 毫无关系的行也被锁了
INSERT INTO lock_demo VALUES (30, 30, 30);         -- 也阻塞

-- 查看锁：PRIMARY 上每一条记录都有 X 锁，外加 supremum（正无穷）
-- [步骤 3][会话 A]
ROLLBACK;
-- 原因：没有索引只能全表扫描聚簇索引，扫描过的每一行都加 Next-Key Lock，等于锁表。
-- 所以 UPDATE / DELETE / FOR UPDATE 的 WHERE 条件一定要走索引


-- =====================================================================
-- 实验 8：死锁
-- =====================================================================
-- 重置数据
-- [步骤 1][会话 A]
BEGIN;
UPDATE account SET balance = balance - 10 WHERE id = 1;   -- 锁住 id = 1

-- [步骤 2][会话 B]
BEGIN;
UPDATE account SET balance = balance - 10 WHERE id = 2;   -- 锁住 id = 2

-- [步骤 3][会话 A]
UPDATE account SET balance = balance + 10 WHERE id = 2;   -- 等 B 释放 id = 2，阻塞

-- [步骤 4][会话 B]
UPDATE account SET balance = balance + 10 WHERE id = 1;   -- 等 A 释放 id = 1，形成环
-- 立刻报错：ERROR 1213 Deadlock found when trying to get lock; try restarting transaction
-- InnoDB 的死锁检测发现等待环，回滚其中代价小的事务（这里是 B），A 的步骤 3 随即执行成功

-- [步骤 5][会话 A]
COMMIT;

-- 查看最近一次死锁的详细信息（在输出里搜索 LATEST DETECTED DEADLOCK）：
SHOW ENGINE INNODB STATUS;
-- 避免死锁：多个事务按相同顺序加锁（比如转账时总是先锁 id 小的账户）；事务尽量小；加锁的条件走索引
