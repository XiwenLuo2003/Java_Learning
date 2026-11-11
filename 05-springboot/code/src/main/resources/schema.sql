DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50),
    age         INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT         NOT NULL,
    product     VARCHAR(100)   NOT NULL,
    amount      DECIMAL(10, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_order_user_id ON t_order (user_id);

DROP TABLE IF EXISTS t_tx_log;
CREATE TABLE t_tx_log (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    content VARCHAR(100) NOT NULL
);
