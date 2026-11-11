-- =====================================================================
-- 05 慢 SQL 定位与优化
-- 套路：慢查询日志找到 SQL → EXPLAIN 看执行计划 → EXPLAIN ANALYZE 看实际耗时 → 改 SQL 或加索引 → 再验证
-- =====================================================================
USE mysql_learning;

-- =====================================================================
-- 实验 1：开启慢查询日志
-- =====================================================================
SHOW VARIABLES LIKE 'slow_query_log%';    -- 是否开启、日志文件位置
SHOW VARIABLES LIKE 'long_query_time';    -- 默认 10 秒

SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;         -- 对之后新建的连接生效
SET SESSION long_query_time = 0.1;        -- 当前连接立即生效

SELECT * FROM user_big WHERE remark = '备注99999';   -- 全表扫描，大概率超过 0.1 秒
-- 打开 slow_query_log_file 指向的文件，能看到这条 SQL、Query_time、Rows_examined（扫描了 10 万行）
-- 线上常用 mysqldumpslow 或 pt-query-digest 按耗时、次数汇总慢日志

-- 实验结束后恢复：
-- SET GLOBAL slow_query_log = OFF;
-- SET GLOBAL long_query_time = 10;

-- =====================================================================
-- 实验 2：深分页
-- =====================================================================
-- 2.1 原始写法：LIMIT 90000, 10 要先找出 90010 条再扔掉前 90000 条，而且每条都要回表
EXPLAIN ANALYZE
SELECT * FROM user_big ORDER BY create_time LIMIT 90000, 10;
-- 优化器可能直接放弃索引，选择全表扫描 + filesort

-- 2.2 优化一：延迟关联。子查询只在 idx_create_time 上找 id（覆盖索引，不回表），最后只回表 10 次
EXPLAIN ANALYZE
SELECT u.*
FROM user_big u
INNER JOIN (
    SELECT id FROM user_big ORDER BY create_time LIMIT 90000, 10
) t ON u.id = t.id
ORDER BY u.create_time;

-- 2.3 优化二：游标分页（推荐）。前端传上一页最后一条的 create_time，直接从那里往后读 10 条
-- 第 90000 条的 create_time 是 '2026-03-04 12:00:00'（初始化时第 n 行 = 2026-01-01 + n 分钟，90000 分钟 = 62.5 天）
EXPLAIN ANALYZE
SELECT * FROM user_big
WHERE create_time > '2026-03-04 12:00:00'
ORDER BY create_time
LIMIT 10;
-- 无论翻到第几页都只扫描 10 行左右；代价是不能直接跳到第 N 页，适合“下拉加载更多”。
-- 排序字段可能重复时（比如两个订单同一秒创建），要带上 id 一起作游标：
--   WHERE (create_time, id) > (?, ?) ORDER BY create_time, id LIMIT 10，并建联合索引 (create_time, id)

-- =====================================================================
-- 实验 3：联合索引“等值在前，范围在后”
-- 查询：北京用户中，2 月第一周注册的
-- =====================================================================
CREATE INDEX idx_city_time ON user_big (city, create_time);
CREATE INDEX idx_time_city ON user_big (create_time, city);

-- 3.1 等值列在前：先定位到 city = '北京'，在这部分数据里 create_time 是有序的，直接取一段，扫描约 2000 行
EXPLAIN ANALYZE
SELECT id FROM user_big FORCE INDEX (idx_city_time)
WHERE city = '北京' AND create_time >= '2026-02-01' AND create_time < '2026-02-08';

-- 3.2 范围列在前：create_time 是范围，后面的 city 无法用来定位，要把这一周所有城市约 1 万行都扫一遍再过滤
EXPLAIN ANALYZE
SELECT id FROM user_big FORCE INDEX (idx_time_city)
WHERE city = '北京' AND create_time >= '2026-02-01' AND create_time < '2026-02-08';

-- 对比两条输出里的 rows 和 actual time，前者扫描的行数约为后者的 1/5

DROP INDEX idx_city_time ON user_big;
DROP INDEX idx_time_city ON user_big;

-- =====================================================================
-- 实验 4：COUNT(*) 为什么慢，怎么优化
-- =====================================================================
EXPLAIN SELECT COUNT(*) FROM user_big;
-- key 不是 PRIMARY，而是最小的二级索引（比如 idx_create_time）：
-- InnoDB 有 MVCC，不同事务看到的行数可能不同，所以不能像 MyISAM 那样存一个总数，只能扫描；
-- 二级索引比聚簇索引小得多，扫描它代价最低。
-- 大表的总数需求：用 Redis 计数、单独的计数表，或者接受 SHOW TABLE STATUS 里的估算值

-- =====================================================================
-- 优化清单（面试时按这个顺序说）
-- =====================================================================
-- 1. 先看 type 是不是 ALL / index，key 是不是 NULL → 加索引，或者改写导致索引失效的写法（02_index.sql 实验 5）
-- 2. 看 key_len → 联合索引用了几列，是否被范围查询截断（02_index.sql 实验 4）
-- 3. 看 Extra：
--      Using filesort  → 让 ORDER BY 走索引（02_index.sql 实验 6）
--      Using temporary → GROUP BY / DISTINCT 用了临时表，考虑给分组字段加索引
--      没有 Using index → 能否改成覆盖索引，减少回表
-- 4. 看 rows → 扫描行数是否远大于返回行数；深分页改延迟关联或游标分页
-- 5. SQL 之外：大表考虑归档、分库分表；读多写少考虑加缓存；复杂报表放到从库或数仓
