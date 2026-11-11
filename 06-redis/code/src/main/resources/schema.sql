CREATE TABLE IF NOT EXISTS tb_shop
(
    id    BIGINT PRIMARY KEY,
    name  VARCHAR(64) NOT NULL,
    score INT         NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_seckill_voucher
(
    voucher_id BIGINT PRIMARY KEY,
    stock      INT NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_voucher_order
(
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT    NOT NULL,
    voucher_id  BIGINT    NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 一人一单的最后一道防线：Redis 判断、消费者幂等都失效时，数据库唯一索引兜底
    CONSTRAINT uk_user_voucher UNIQUE (user_id, voucher_id)
);
