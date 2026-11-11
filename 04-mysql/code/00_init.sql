-- =====================================================================
-- 00 初始化：建库、建表、造数据。所有实验脚本执行前先完整执行一遍本文件。
-- 环境：MySQL 8.0.18+（用到窗口函数、递归 CTE、EXPLAIN ANALYZE）
-- 可以重复执行，每次都会删库重建
-- =====================================================================

DROP DATABASE IF EXISTS mysql_learning;
CREATE DATABASE mysql_learning DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE mysql_learning;

-- ---------------------------------------------------------------------
-- 1. SQL 实战用的小表（01_sql_practice.sql）
-- ---------------------------------------------------------------------

CREATE TABLE dept (
    id   INT PRIMARY KEY,
    name VARCHAR(20) NOT NULL
) COMMENT '部门';

CREATE TABLE emp (
    id        INT PRIMARY KEY,
    name      VARCHAR(20)   NOT NULL,
    dept_id   INT           NULL COMMENT '允许为空：有个员工还没分配部门',
    salary    DECIMAL(10, 2) NOT NULL,
    hire_date DATE          NOT NULL
) COMMENT '员工';

INSERT INTO dept VALUES
    (1, '研发部'),
    (2, '产品部'),
    (3, '市场部'),
    (4, '法务部');          -- 法务部没有员工，用来观察 LEFT JOIN

INSERT INTO emp VALUES
    (1, '张三', 1, 30000, '2020-03-01'),
    (2, '李四', 1, 25000, '2021-06-15'),
    (3, '王五', 1, 25000, '2022-01-10'),   -- 和李四同薪，用来观察三种排名函数的区别
    (4, '赵六', 2, 20000, '2019-11-20'),
    (5, '孙七', 2, 18000, '2023-02-01'),
    (6, '周八', 3, 15000, '2021-09-01'),
    (7, '吴九', NULL, 12000, '2024-01-05'), -- 没有部门
    (8, '郑十', 1, 20000, '2023-07-01');

CREATE TABLE orders (
    id          INT PRIMARY KEY,
    user_id     INT            NOT NULL,
    amount      DECIMAL(10, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL COMMENT 'PAID / CANCELLED',
    create_time DATETIME       NOT NULL
) COMMENT '订单';

INSERT INTO orders VALUES
    (1,  101, 100.00, 'PAID',      '2026-11-01 10:00:00'),
    (2,  101,  50.00, 'PAID',      '2026-11-02 09:30:00'),
    (3,  101,  80.00, 'PAID',      '2026-11-03 20:15:00'),
    (4,  102, 200.00, 'PAID',      '2026-11-01 12:00:00'),
    (5,  102,  30.00, 'CANCELLED', '2026-11-02 18:00:00'),
    (6,  102,  60.00, 'PAID',      '2026-11-04 08:00:00'),
    (7,  103, 120.00, 'PAID',      '2026-11-02 11:00:00'),
    (8,  103,  40.00, 'PAID',      '2026-11-03 15:00:00'),
    (9,  103,  70.00, 'PAID',      '2026-11-04 16:30:00'),
    (10, 103,  90.00, 'PAID',      '2026-11-04 21:00:00');

-- ---------------------------------------------------------------------
-- 2. 索引 / 慢 SQL 用的大表（02_index.sql、05_slow_sql.sql）
-- ---------------------------------------------------------------------

CREATE TABLE user_big (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    age         INT         NOT NULL,
    city        VARCHAR(20) NOT NULL,
    phone       VARCHAR(11) NOT NULL COMMENT '字符串类型，用来演示隐式类型转换导致索引失效',
    create_time DATETIME    NOT NULL,
    remark      VARCHAR(200) NULL COMMENT '没有索引的列',
    KEY idx_name (name),
    KEY idx_city_age_name (city, age, name),
    KEY idx_phone (phone),
    KEY idx_create_time (create_time)
) COMMENT '10 万行测试用户';

-- 递归 CTE 生成 1 ~ 100000 的序列，默认递归深度只有 1000，要先调大
SET SESSION cte_max_recursion_depth = 200000;

INSERT INTO user_big (name, age, city, phone, create_time, remark)
WITH RECURSIVE seq (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 100000
)
SELECT CONCAT('user', n),
       18 + n % 50,
       ELT(n % 5 + 1, '北京', '上海', '广州', '深圳', '杭州'),
       CONCAT('138', LPAD(n, 8, '0')),
       '2026-01-01 00:00:00' + INTERVAL n MINUTE,
       CONCAT('备注', n)
FROM seq;

-- 更新统计信息，让优化器的估算更准
ANALYZE TABLE user_big;

-- ---------------------------------------------------------------------
-- 3. 事务 / 锁实验用的表（03_transaction_lock.sql）
-- ---------------------------------------------------------------------

CREATE TABLE account (
    id      INT PRIMARY KEY,
    name    VARCHAR(20)    NOT NULL,
    balance DECIMAL(10, 2) NOT NULL
) COMMENT '账户';

INSERT INTO account VALUES (1, 'A', 100.00), (2, 'B', 100.00);

CREATE TABLE lock_demo (
    id INT PRIMARY KEY,
    k  INT NOT NULL COMMENT '普通二级索引',
    v  INT NOT NULL COMMENT '没有索引',
    KEY idx_k (k)
) COMMENT '间隙锁实验：id / k 都是 5、10、15、20，中间留出间隙';

INSERT INTO lock_demo VALUES (5, 5, 5), (10, 10, 10), (15, 15, 15), (20, 20, 20);

-- ---------------------------------------------------------------------
-- 检查：下面几个数字对上就说明初始化成功
-- ---------------------------------------------------------------------
SELECT (SELECT COUNT(*) FROM emp)       AS emp_rows,        -- 8
       (SELECT COUNT(*) FROM orders)    AS order_rows,      -- 10
       (SELECT COUNT(*) FROM user_big)  AS user_big_rows,   -- 100000
       (SELECT COUNT(*) FROM lock_demo) AS lock_demo_rows;  -- 4
