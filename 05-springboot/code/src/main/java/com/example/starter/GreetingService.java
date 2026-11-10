package com.example.starter;

/**
 * 模拟一个第三方 starter 提供的功能类。注意它没有任何 Spring 注解，是被自动配置类用 @Bean 注册的。
 */
public class GreetingService {

    private final String prefix;

    public GreetingService(String prefix) {
        this.prefix = prefix;
    }

    public String greet(String name) {
        return prefix + ", " + name + "!";
    }
}
