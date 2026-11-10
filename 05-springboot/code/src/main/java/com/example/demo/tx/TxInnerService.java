package com.example.demo.tx;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 被 TxDemoService 调用的“内层”服务。必须是另一个 Bean，跨 Bean 调用才会经过代理，传播行为才生效。
 */
@Service
public class TxInnerService {

    private final JdbcTemplate jdbcTemplate;

    public TxInnerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** REQUIRES_NEW：挂起外层事务，自己开一个全新的事务，提交 / 回滚互不影响 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInNewTransaction(String content) {
        jdbcTemplate.update("INSERT INTO t_tx_log (content) VALUES (?)", content);
    }

    /** 默认 REQUIRED：加入外层事务。这里抛异常时，代理会把整个共享事务标记为 rollback-only */
    @Transactional
    public void insertThenFail(String content) {
        jdbcTemplate.update("INSERT INTO t_tx_log (content) VALUES (?)", content);
        throw new IllegalStateException("内层方法异常");
    }
}
