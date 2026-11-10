package com.example.demo.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 容器启动完成后执行（所有单例 Bean 都已创建好），打印循环依赖结果和接口入口。
 */
@Component
public class StartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final CircularA circularA;

    public StartupRunner(CircularA circularA) {
        this.circularA = circularA;
    }

    @Override
    public void run(String... args) {
        boolean same = circularA.getCircularB().getCircularA() == circularA;
        log.info("[循环依赖] A -> B -> A 拿到的是同一个 A 吗？ {}", same);
        log.info("启动完成，接口清单见 requests.http；事务失效场景入口: http://localhost:8080/demo/tx");
    }
}
