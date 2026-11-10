package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动类。@SpringBootApplication 是三个注解的组合：
 * <ul>
 *   <li>@SpringBootConfiguration：本质是 @Configuration，标记这是一个配置类</li>
 *   <li>@ComponentScan：扫描当前包（com.example.demo）及子包下的 @Component / @Service / @Controller ...</li>
 *   <li>@EnableAutoConfiguration：自动配置的入口，见 com.example.starter 和八股“自动配置原理”</li>
 * </ul>
 * 注意 com.example.starter 不在 com.example.demo 下，它不是被扫描进来的，而是被自动配置机制加载的。
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
