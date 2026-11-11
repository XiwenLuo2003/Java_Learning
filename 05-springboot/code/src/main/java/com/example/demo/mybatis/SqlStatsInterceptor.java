package com.example.demo.mybatis;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis 插件：统计每条 SQL 的耗时，超过阈值打 WARN；同时可以按线程记录执行过的 SQL，供演示接口返回。
 * <p>
 * 插件原理：MyBatis 允许拦截四大对象 —— Executor、StatementHandler、ParameterHandler、ResultSetHandler。
 * 创建这些对象时，Configuration 会调用 InterceptorChain.pluginAll，用每个插件的 plugin() 方法
 * （默认实现是 Plugin.wrap，基于 JDK 动态代理）把目标对象层层包起来。
 * 调用到 @Signature 声明的方法时进入 intercept()，invocation.proceed() 继续调用下一层，和 AOP 的 @Around 一个思路。
 * <p>
 * 注册方式：在 Spring Boot 中声明成 Bean 即可，MyBatis-Plus 的自动配置会把容器里所有 Interceptor 加进 Configuration。
 * 分页插件 MybatisPlusInterceptor 也是这样注册的（见 config/MybatisPlusConfig）。
 */
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SqlStatsInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlStatsInterceptor.class);

    /** 非 null 时表示当前线程正在记录 SQL */
    private static final ThreadLocal<List<String>> RECORDED_SQLS = new ThreadLocal<>();

    private final long slowSqlMs;

    public SqlStatsInterceptor(@Value("${demo.mybatis.slow-sql-ms:100}") long slowSqlMs) {
        this.slowSqlMs = slowSqlMs;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        String sql = handler.getBoundSql().getSql().replaceAll("\\s+", " ").trim();

        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= slowSqlMs) {
                log.warn("[慢 SQL] {} ms: {}", cost, sql);
            } else {
                log.info("[SQL] {} ms: {}", cost, sql);
            }
            List<String> recorded = RECORDED_SQLS.get();
            if (recorded != null) {
                recorded.add(sql);
            }
        }
    }

    /** 开始记录当前线程执行的 SQL */
    public static void startRecording() {
        RECORDED_SQLS.set(new ArrayList<>());
    }

    /** 结束记录并返回记录到的 SQL；和 UserContext 一样，ThreadLocal 用完必须 remove */
    public static List<String> stopRecording() {
        List<String> recorded = RECORDED_SQLS.get();
        RECORDED_SQLS.remove();
        return recorded == null ? List.of() : recorded;
    }
}
