package com.example.demo.lifecycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 循环依赖：A 依赖 B，B 依赖 A。
 * <p>
 * 字段注入（或 setter 注入）能被三级缓存解决：A 实例化后先把“半成品”放进缓存，再去注入 B，
 * B 注入 A 时直接从缓存拿到这个半成品。
 * <p>
 * 动手试试：把两个类都改成构造器注入，启动会直接报 BeanCurrentlyInCreationException ——
 * 构造器注入时 A 连实例都还没有，没有半成品可以提前暴露。
 * 另外 application.yml 里的 allow-circular-references 去掉后，即使字段注入也会启动失败（Spring Boot 2.6+ 默认禁止）。
 */
@Component
public class CircularA {

    @Autowired
    private CircularB circularB;

    public CircularB getCircularB() {
        return circularB;
    }
}
