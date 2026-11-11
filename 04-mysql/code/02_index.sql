-- =====================================================================
-- 02 索引：B+ 树、回表、覆盖索引、最左前缀、索引失效、排序
-- 表：user_big（10 万行），索引：
--   PRIMARY(id)、idx_name(name)、idx_city_age_name(city, age, name)、idx_phone(phone)、idx_create_time(create_time)
--
-- EXPLAIN 重点看 4 列：
--   type     访问类型，从好到差：const > eq_ref > ref > range > index > ALL（ALL = 全表扫描）
--   key      实际用到的索引，NULL 表示没用索引
--   key_len  用到的索引长度，用来判断联合索引用了几列（计算规则见实验 5）
--   Extra    Using index = 覆盖索引，不回表；Using index condition = 索引下推；
--            Using filesort = 额外排序；Using where = 在 Server 层再过滤
-- 注意：rows 是估算值，每台机器略有不同；type / key / Extra 应该和预期一致
-- =====================================================================
USE mysql_learning;

-- =====================================================================
-- 实验 1：B+ 树能存多少数据
-- =====================================================================
-- InnoDB 一页 16KB。非叶子节点只存“主键 + 页指针”：BIGINT 8 字节 + 指针 6 字节 = 14 字节，
-- 一页能存 16384 / 14 ≈ 1170 个指针。假设一行数据 1KB，叶子节点一页存 16 行。
--   2 层：1170 × 16          ≈ 1.8 万行
--   3 层：1170 × 1170 × 16   ≈ 2190 万行
-- 所以千万级的表，主键查询一般只需要 3 次磁盘 IO（根节点常驻内存的话只要 2 次）。
SHOW VARIABLES LIKE 'innodb_page_size';   -- 16384
SHOW INDEX FROM user_big;                 -- 看每个索引包含哪些列、Cardinality（区分度估算）

-- =====================================================================
-- 实验 2：主键查询 vs 二级索引查询（回表）
-- =====================================================================

-- 2.1 主键等值：type = const，直接在聚簇索引的叶子节点拿到整行
EXPLAIN SELECT * FROM user_big WHERE id = 100;

-- 2.2 二级索引等值，查整行：type = ref，key = idx_name，Extra 为空
-- 过程：先在 idx_name 树找到 name='user100' 对应的主键 id，再拿 id 回聚簇索引查整行 —— 这就是“回表”
EXPLAIN SELECT * FROM user_big WHERE name = 'user100';

-- =====================================================================
-- 实验 3：覆盖索引（不回表）
-- =====================================================================

-- 3.1 只查索引里有的列：Extra = Using index
-- 二级索引的叶子节点存的是“索引列 + 主键”，所以 id 也在里面，不用回表
EXPLAIN SELECT id, name FROM user_big WHERE name = 'user100';

-- 3.2 多查一个 age（不在 idx_name 里）：Extra 的 Using index 消失，又要回表了
EXPLAIN SELECT id, name, age FROM user_big WHERE name = 'user100';

-- 3.3 联合索引覆盖：city、age、name、id 都在 idx_city_age_name 里，Extra = Using index
EXPLAIN SELECT id, city, age, name FROM user_big WHERE city = '北京' AND age = 20;
-- 这就是“SELECT * 要少用”的原因之一：只查需要的列，才有机会用上覆盖索引

-- =====================================================================
-- 实验 4：最左前缀原则（联合索引 idx_city_age_name(city, age, name)）
-- 联合索引的 B+ 树先按 city 排序，city 相同再按 age 排，age 相同再按 name 排
-- =====================================================================

-- 4.1 用到 city：key_len = 82
EXPLAIN SELECT * FROM user_big WHERE city = '北京';
-- 注意：北京占 20% 的数据，优化器可能认为“回表 2 万次还不如全表扫描”，type 可能是 ALL。
-- 这说明索引不是一定会用，优化器按成本选择。换成覆盖查询就一定会用：
EXPLAIN SELECT id FROM user_big WHERE city = '北京';

-- 4.2 用到 city + age：key_len = 86
EXPLAIN SELECT * FROM user_big WHERE city = '北京' AND age = 20;

-- 4.3 三列全用上：key_len = 288
EXPLAIN SELECT * FROM user_big WHERE city = '北京' AND age = 20 AND name = 'user5';

-- 4.4 WHERE 里条件的书写顺序无所谓，优化器会调整：和 4.3 完全一样，key_len = 288
EXPLAIN SELECT * FROM user_big WHERE name = 'user5' AND age = 20 AND city = '北京';

-- 4.5 跳过最左列 city：SELECT * 用不了 idx_city_age_name，type = ALL
EXPLAIN SELECT * FROM user_big WHERE age = 20;
-- MySQL 8.0.13+ 的“索引跳跃扫描”：最左列区分度很低（city 只有 5 个值）且是覆盖查询时，
-- 可能看到 Extra = Using index for skip scan，相当于把 5 个 city 各查一遍
EXPLAIN SELECT city, age, name FROM user_big WHERE age = 20;

-- 4.6 中间断开（有 city 和 name，没有 age）：key_len = 82，只有 city 用来定位
-- name 的条件通过“索引下推”在存储引擎层用索引里的 name 先过滤，减少回表，Extra = Using index condition
EXPLAIN SELECT * FROM user_big WHERE city = '北京' AND name = 'user5';

-- 4.7 范围查询之后的列用不上：age 是范围，key_len = 86（city + age），name 只能靠索引下推过滤
EXPLAIN SELECT * FROM user_big WHERE city = '北京' AND age > 60 AND name = 'user5';
-- 设计联合索引的口诀“等值在前，范围在后”就是这么来的，见 05_slow_sql.sql 实验 3

-- key_len 计算规则（utf8mb4 一个字符最多 4 字节）：
--   VARCHAR(n) NOT NULL = n × 4 + 2（2 字节存长度），允许 NULL 再 + 1
--   INT = 4，BIGINT = 8，DATETIME = 5，允许 NULL 再 + 1
--   city VARCHAR(20) = 82，age INT = 4，name VARCHAR(50) = 202 → 82 / 86 / 288

-- =====================================================================
-- 实验 5：索引失效的常见场景（每组前一条失效，后一条是正确写法）
-- =====================================================================

-- 5.1 对索引列使用函数：type = ALL
EXPLAIN SELECT * FROM user_big WHERE LEFT(name, 6) = 'user99';
EXPLAIN SELECT * FROM user_big WHERE name LIKE 'user99%';            -- type = range

-- 5.2 对索引列做运算：type = ALL
EXPLAIN SELECT * FROM user_big WHERE id + 1 = 100;
EXPLAIN SELECT * FROM user_big WHERE id = 99;                       -- type = const

-- 5.3 隐式类型转换：phone 是字符串，和数字比较时 MySQL 会把 phone 转成数字，相当于对索引列用了函数，type = ALL
EXPLAIN SELECT * FROM user_big WHERE phone = 13800000100;
EXPLAIN SELECT * FROM user_big WHERE phone = '13800000100';         -- type = ref
-- 反过来不会失效：id 是数字列，id = '100' 转换的是右边的常量，索引照常使用
EXPLAIN SELECT * FROM user_big WHERE id = '100';                    -- type = const

-- 5.4 LIKE 以 % 开头：B+ 树按前缀有序，不知道从哪开始找，type = ALL
EXPLAIN SELECT * FROM user_big WHERE name LIKE '%99';
EXPLAIN SELECT * FROM user_big WHERE name LIKE 'user99%';           -- type = range

-- 5.5 OR 连接了没有索引的列：只要有一边没索引，就只能全表扫描
EXPLAIN SELECT * FROM user_big WHERE name = 'user1' OR remark = '备注2';
EXPLAIN SELECT * FROM user_big WHERE name = 'user1' OR phone = '13800000002';
-- 两边都有索引：type = index_merge，Extra = Using union(idx_name,idx_phone)

-- 5.6 联合索引不满足最左前缀：见实验 4.5

-- =====================================================================
-- 实验 6：ORDER BY 能否利用索引（避免 Using filesort）
-- =====================================================================

-- 6.1 按索引列排序 + LIMIT：直接顺着 idx_create_time 读前 10 条，没有 filesort
EXPLAIN SELECT * FROM user_big ORDER BY create_time LIMIT 10;

-- 6.2 按没有索引的列排序：Extra = Using filesort
EXPLAIN SELECT * FROM user_big ORDER BY remark LIMIT 10;

-- 6.3 联合索引：city、age 等值后，name 本身就是有序的，没有 filesort
EXPLAIN SELECT * FROM user_big WHERE city = '北京' AND age = 20 ORDER BY name;

-- 6.4 跳过了 age：city 相同的数据里 name 不是全局有序的，Extra = Using filesort
EXPLAIN SELECT * FROM user_big WHERE city = '北京' ORDER BY name LIMIT 10;

-- =====================================================================
-- 实验 7：真实项目反例 —— 黑马点评的 tb_voucher_order 只有主键
-- 业务里“一人一单”的判断是：SELECT COUNT(*) FROM tb_voucher_order WHERE user_id = ? AND voucher_id = ?
-- 没有索引就是全表扫描。合理的设计是建一个唯一联合索引：
--   ALTER TABLE tb_voucher_order ADD UNIQUE KEY uk_user_voucher (user_id, voucher_id);
-- 一举两得：查询走索引；并发下即使代码层的锁失效，数据库也能用唯一约束兜底，保证一人一单
-- =====================================================================
