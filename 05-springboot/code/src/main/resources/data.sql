-- 演示用明文密码，真实项目要用 BCrypt 等哈希存储
INSERT INTO t_user (username, password, nickname, age) VALUES ('admin', '123456', '管理员', 30);
INSERT INTO t_user (username, password, nickname, age) VALUES ('zhangsan', '123456', '张三', 22);
INSERT INTO t_user (username, password, nickname, age) VALUES ('lisi', '123456', '李四', 25);
INSERT INTO t_user (username, password, nickname, age) VALUES ('wangwu', '123456', '王五', 28);
INSERT INTO t_user (username, password, nickname, age) VALUES ('zhaoliu', '123456', '赵六', 35);

-- 订单：admin 3 单、zhangsan 2 单、lisi 1 单，wangwu 和 zhaoliu 没有订单（用来观察 LEFT JOIN 和一对多映射）
INSERT INTO t_order (user_id, product, amount, status) VALUES (1, '机械键盘', 399.00, 'PAID');
INSERT INTO t_order (user_id, product, amount, status) VALUES (1, '显示器', 1299.00, 'PAID');
INSERT INTO t_order (user_id, product, amount, status) VALUES (1, '鼠标垫', 29.90, 'CANCELLED');
INSERT INTO t_order (user_id, product, amount, status) VALUES (2, 'Java 编程思想', 108.00, 'PAID');
INSERT INTO t_order (user_id, product, amount, status) VALUES (2, '咖啡豆', 89.00, 'PAID');
INSERT INTO t_order (user_id, product, amount, status) VALUES (3, '耳机', 599.00, 'PAID');
