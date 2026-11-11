-- =====================================================================
-- 01 SQL 实战：JOIN、GROUP BY / HAVING、窗口函数（小红书、美团一面手写 SQL 原题风格）
-- 用法：先遮住答案自己写，再执行答案对照预期结果
-- =====================================================================
USE mysql_learning;

-- =====================================================================
-- 题 1：INNER JOIN / LEFT JOIN / RIGHT JOIN 的区别
-- =====================================================================

-- 1.1 INNER JOIN：只保留两边都能匹配上的行
SELECT e.name, d.name AS dept_name
FROM emp e
INNER JOIN dept d ON e.dept_id = d.id
ORDER BY e.id;
-- 预期 7 行：吴九（没部门）和法务部（没员工）都不出现

-- 1.2 LEFT JOIN：左表全保留，右表匹配不上的补 NULL
SELECT d.name AS dept_name, e.name
FROM dept d
LEFT JOIN emp e ON e.dept_id = d.id
ORDER BY d.id, e.id;
-- 预期 8 行：多出一行“法务部 | NULL”

-- 1.3 RIGHT JOIN：右表全保留（等价于把两张表换位置写 LEFT JOIN，实际开发基本只用 LEFT JOIN）
SELECT d.name AS dept_name, e.name
FROM dept d
RIGHT JOIN emp e ON e.dept_id = d.id
ORDER BY e.id;
-- 预期 8 行：多出一行“NULL | 吴九”

-- 1.4 易错点：LEFT JOIN 的过滤条件写在 ON 和 WHERE 里结果不同
SELECT d.name AS dept_name, e.name
FROM dept d
LEFT JOIN emp e ON e.dept_id = d.id AND e.salary >= 25000
ORDER BY d.id, e.id;
-- 预期 6 行：条件在 ON 里只影响“右表能不能匹配上”，左表 4 个部门都保留
--   研发部 | 张三；研发部 | 李四；研发部 | 王五；产品部 | NULL；市场部 | NULL；法务部 | NULL

SELECT d.name AS dept_name, e.name
FROM dept d
LEFT JOIN emp e ON e.dept_id = d.id
WHERE e.salary >= 25000
ORDER BY d.id, e.id;
-- 预期 3 行：WHERE 在连接之后过滤，补出来的 NULL 行被过滤掉，LEFT JOIN 退化成了 INNER JOIN

-- =====================================================================
-- 题 2：统计每个部门的人数和平均薪资，只显示人数 >= 2 的部门（GROUP BY + HAVING）
-- =====================================================================
SELECT d.name              AS dept_name,
       COUNT(*)            AS emp_count,
       ROUND(AVG(e.salary), 2) AS avg_salary
FROM emp e
INNER JOIN dept d ON e.dept_id = d.id
GROUP BY d.id, d.name
HAVING COUNT(*) >= 2
ORDER BY avg_salary DESC;
-- 预期：
--   研发部 | 4 | 25000.00
--   产品部 | 2 | 19000.00
-- WHERE vs HAVING：WHERE 在分组前过滤“行”，不能用聚合函数；HAVING 在分组后过滤“组”，可以用聚合函数

-- =====================================================================
-- 题 3：COUNT(*)、COUNT(1)、COUNT(列) 的区别
-- =====================================================================
SELECT COUNT(*)                AS count_star,      -- 8：统计行数，包括 NULL
       COUNT(1)                AS count_one,       -- 8：和 COUNT(*) 一样，InnoDB 对两者的处理没有性能差别
       COUNT(dept_id)          AS count_dept_id,   -- 7：只统计该列不为 NULL 的行，吴九不算
       COUNT(DISTINCT dept_id) AS count_distinct   -- 3：去重后不为 NULL 的值有 1、2、3
FROM emp;

-- =====================================================================
-- 题 4：查询每个部门薪资最高的员工
-- =====================================================================

-- 写法 A：窗口函数（推荐，MySQL 8.0+）
SELECT dept_name, name, salary
FROM (
    SELECT d.name AS dept_name, e.name, e.salary,
           ROW_NUMBER() OVER (PARTITION BY e.dept_id ORDER BY e.salary DESC, e.id) AS rn
    FROM emp e
    INNER JOIN dept d ON e.dept_id = d.id
) t
WHERE rn = 1
ORDER BY salary DESC;
-- 预期：研发部 | 张三 | 30000；产品部 | 赵六 | 20000；市场部 | 周八 | 15000

-- 写法 B：子查询（MySQL 5.7 也能用；如果最高薪有并列，会全部返回）
SELECT d.name AS dept_name, e.name, e.salary
FROM emp e
INNER JOIN dept d ON e.dept_id = d.id
WHERE (e.dept_id, e.salary) IN (
    SELECT dept_id, MAX(salary) FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id
)
ORDER BY e.salary DESC;

-- =====================================================================
-- 题 5：ROW_NUMBER、RANK、DENSE_RANK 的区别（部门内薪资排名）
-- =====================================================================
SELECT e.name, e.salary,
       ROW_NUMBER() OVER (ORDER BY e.salary DESC, e.id) AS row_num,    -- 连续编号，并列也分先后：1 2 3 4
       RANK()       OVER (ORDER BY e.salary DESC)       AS rnk,        -- 并列同名次，后面跳号：1 2 2 4
       DENSE_RANK() OVER (ORDER BY e.salary DESC)       AS dense_rnk   -- 并列同名次，后面不跳号：1 2 2 3
FROM emp e
WHERE e.dept_id = 1
ORDER BY row_num;
-- 预期（研发部）：
--   张三 | 30000 | 1 | 1 | 1
--   李四 | 25000 | 2 | 2 | 2
--   王五 | 25000 | 3 | 2 | 2
--   郑十 | 20000 | 4 | 4 | 3
-- 易错点：RANK / DENSE_RANK 按 ORDER BY 的全部列判断是否并列。
-- 如果像 ROW_NUMBER 那样写成 ORDER BY salary DESC, id，就永远不会并列，排名退化成 1 2 3 4。
-- 想看所有部门各自排名，在 OVER 里加 PARTITION BY dept_id 即可。

-- =====================================================================
-- 题 6：第二高的薪资（LeetCode 176），没有则返回 NULL
-- =====================================================================
SELECT (
    SELECT DISTINCT salary FROM emp ORDER BY salary DESC LIMIT 1 OFFSET 1
) AS second_highest_salary;
-- 预期：25000.00（30000 之后去重的第二个值；外面包一层 SELECT 保证没有数据时返回 NULL 而不是空集）

-- =====================================================================
-- 题 7：每个用户已支付订单的数量和总金额，只看总金额 > 250 的用户
-- =====================================================================
SELECT user_id, COUNT(*) AS order_count, SUM(amount) AS total_amount
FROM orders
WHERE status = 'PAID'          -- 先用 WHERE 过滤行
GROUP BY user_id
HAVING SUM(amount) > 250       -- 再用 HAVING 过滤组
ORDER BY total_amount DESC;
-- 预期：103 | 4 | 320.00；102 | 2 | 260.00（101 只有 230，被过滤）

-- =====================================================================
-- 题 8：每个用户最近的一笔订单
-- =====================================================================
SELECT user_id, id AS order_id, amount, create_time
FROM (
    SELECT o.*, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY create_time DESC) AS rn
    FROM orders o
) t
WHERE rn = 1
ORDER BY user_id;
-- 预期：
--   101 | 3  | 80.00 | 2026-11-03 20:15:00
--   102 | 6  | 60.00 | 2026-11-04 08:00:00
--   103 | 10 | 90.00 | 2026-11-04 21:00:00

-- =====================================================================
-- 题 9：每天的 GMV（已支付金额）和截至当天的累计 GMV
-- =====================================================================
SELECT pay_date,
       daily_gmv,
       SUM(daily_gmv) OVER (ORDER BY pay_date) AS cumulative_gmv
FROM (
    SELECT DATE(create_time) AS pay_date, SUM(amount) AS daily_gmv
    FROM orders
    WHERE status = 'PAID'
    GROUP BY DATE(create_time)
) t
ORDER BY pay_date;
-- 预期：
--   2026-11-01 | 300.00 | 300.00
--   2026-11-02 | 170.00 | 470.00   （5 号订单已取消，不计入）
--   2026-11-03 | 120.00 | 590.00
--   2026-11-04 | 220.00 | 810.00

-- =====================================================================
-- 题 10：连续 3 天及以上都有已支付订单的用户（经典“连续登录”题）
-- 思路：同一用户的日期按顺序编号，日期 - 编号 得到的值相同，就说明这些日期是连续的
-- =====================================================================
SELECT user_id,
       MIN(pay_date) AS start_date,
       MAX(pay_date) AS end_date,
       COUNT(*)      AS days
FROM (
    SELECT user_id, pay_date,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY pay_date) AS rn
    FROM (
        SELECT DISTINCT user_id, DATE(create_time) AS pay_date   -- 同一天多单只算一天
        FROM orders
        WHERE status = 'PAID'
    ) d
) t
GROUP BY user_id, DATE_SUB(pay_date, INTERVAL rn DAY)
HAVING COUNT(*) >= 3
ORDER BY user_id;
-- 预期：
--   101 | 2026-11-01 | 2026-11-03 | 3
--   103 | 2026-11-02 | 2026-11-04 | 3
-- 102 的已支付日期是 11-01、11-04，不连续
