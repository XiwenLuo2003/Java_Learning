package com.example.demo.tx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional 失效场景合集。每个方法都先往 t_tx_log 插数据，再制造异常，
 * 最后看表里还剩几行：0 行 = 回滚成功，大于 0 = 事务没按预期回滚。
 * <p>
 * 原理一句话：@Transactional 靠 AOP 代理实现。代理在调用目标方法前从连接池拿一个连接、关闭自动提交，
 * 绑定到当前线程（TransactionSynchronizationManager 的 ThreadLocal）；方法里的 SQL 都用这个连接执行；
 * 方法返回后提交，抛出 RuntimeException / Error 时回滚。
 * 所以凡是“绕过了代理”“换了线程”“异常没抛到代理”的情况，事务都会失效。
 */
@Service
public class TxDemoService {

    private static final Logger log = LoggerFactory.getLogger(TxDemoService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TxInnerService innerService;

    /** 注入自己的代理对象，用来修复自调用；@Lazy 注入的是一个延迟代理，避免启动时的循环依赖问题 */
    @Autowired
    @Lazy
    private TxDemoService self;

    public TxDemoService(JdbcTemplate jdbcTemplate, TxInnerService innerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.innerService = innerService;
    }

    // ---------------- 场景 1：正常回滚（对照组） ----------------

    @Transactional
    public void normalRollback() {
        insert("正常回滚");
        throw new IllegalStateException("运行时异常");
    }

    // ---------------- 场景 2、3：自调用 ----------------

    /** 外层没有 @Transactional，通过 this 调用内部的事务方法：this 是原对象，不经过代理，事务不生效 */
    public void selfInvocation() {
        this.transactionalInner("自调用");
    }

    /** 修复：通过代理对象 self 调用（也可以 AopContext.currentProxy()，或者拆到另一个 Bean） */
    public void selfInvocationFixed() {
        self.transactionalInner("自调用-已修复");
    }

    @Transactional
    public void transactionalInner(String content) {
        insert(content);
        throw new IllegalStateException("运行时异常");
    }

    // ---------------- 场景 4、5：受检异常 ----------------

    /** 默认只对 RuntimeException 和 Error 回滚，受检异常（Exception 的非运行时子类）会照常提交 */
    @Transactional
    public void checkedException() throws Exception {
        insert("受检异常");
        throw new Exception("受检异常，默认不回滚");
    }

    /** 修复：rollbackFor = Exception.class，阿里规范要求显式写上 */
    @Transactional(rollbackFor = Exception.class)
    public void checkedExceptionFixed() throws Exception {
        insert("受检异常-已修复");
        throw new Exception("受检异常，指定了 rollbackFor");
    }

    // ---------------- 场景 6：异常被吞 ----------------

    /** 代理是靠“方法抛出异常”来决定回滚的，自己 catch 掉不再抛，代理就以为成功了 */
    @Transactional
    public void swallowException() {
        insert("异常被吞");
        try {
            throw new IllegalStateException("运行时异常");
        } catch (IllegalStateException e) {
            log.warn("异常被 catch 吞掉了: {}", e.getMessage());
        }
    }

    // ---------------- 场景 7：多线程 ----------------

    /** 事务连接绑定在当前线程上，新线程拿不到，只能从连接池另拿一个自动提交的连接 */
    @Transactional
    public void multiThread() throws InterruptedException {
        insert("主线程写入");
        Thread worker = new Thread(() -> insert("子线程写入"));
        worker.start();
        worker.join();
        throw new IllegalStateException("主线程异常");
    }

    // ---------------- 场景 8、9：传播行为 ----------------

    /** 内层 REQUIRES_NEW 是独立事务，已经提交了，外层回滚不影响它 */
    @Transactional
    public void requiresNew() {
        insert("外层写入");
        innerService.insertInNewTransaction("内层 REQUIRES_NEW 写入");
        throw new IllegalStateException("外层异常");
    }

    /**
     * 内层 REQUIRED 抛异常，外层 catch 住想“继续提交”：
     * 内层代理已经把共享事务标记为 rollback-only，外层提交时发现标记，整体回滚并抛 UnexpectedRollbackException。
     * 想让内层失败不影响外层：内层改 REQUIRES_NEW 或 NESTED，或者内层干脆不加事务。
     */
    @Transactional
    public void rollbackOnly() {
        insert("外层写入");
        try {
            innerService.insertThenFail("内层 REQUIRED 写入");
        } catch (IllegalStateException e) {
            log.warn("外层 catch 了内层异常，以为可以继续提交: {}", e.getMessage());
        }
    }

    // ---------------- 工具方法（不加事务，自动提交） ----------------

    public void clear() {
        jdbcTemplate.update("DELETE FROM t_tx_log");
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_tx_log", Long.class);
        return count == null ? 0 : count;
    }

    private void insert(String content) {
        jdbcTemplate.update("INSERT INTO t_tx_log (content) VALUES (?)", content);
    }
}
