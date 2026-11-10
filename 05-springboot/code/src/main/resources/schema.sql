DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50),
    age         INT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS t_tx_log;
CREATE TABLE t_tx_log (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    content VARCHAR(100) NOT NULL
);
