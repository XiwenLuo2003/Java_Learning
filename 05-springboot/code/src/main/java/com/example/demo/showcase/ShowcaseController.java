package com.example.demo.showcase;

import com.example.demo.common.BizException;
import com.example.demo.common.Result;
import com.example.demo.lifecycle.CircularA;
import com.example.demo.tx.TxDemoService;
import com.example.demo.user.UserMapper;
import com.example.demo.user.UserService;
import com.example.starter.GreetingService;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原理观察接口，不需要登录。
 */
@RestController
@RequestMapping("/demo")
public class ShowcaseController {

    private final UserService userService;
    private final TxDemoService txDemoService;
    private final UserMapper userMapper;
    private final CircularA circularA;
    private final ObjectProvider<GreetingService> greetingService;

    public ShowcaseController(UserService userService, TxDemoService txDemoService, UserMapper userMapper,
                              CircularA circularA, ObjectProvider<GreetingService> greetingService) {
        this.userService = userService;
        this.txDemoService = txDemoService;
        this.userMapper = userMapper;
        this.circularA = circularA;
        this.greetingService = greetingService;
    }

    /**
     * 看看注入进来的到底是什么对象。预期：
     * <ul>
     *   <li>UserService、TxDemoService：有 @Transactional / @LogExecution，是 CGLIB 代理（类名带 $$SpringCGLIB$$）</li>
     *   <li>UserMapper：MyBatis 生成的 JDK 动态代理（jdk.proxy...$ProxyNN），isJdkProxyClass=true；
     *       但它不是 Spring AOP 生成的，所以 isSpringAopProxy=false</li>
     *   <li>CircularA：没有任何切面，就是原对象</li>
     * </ul>
     */
    @GetMapping("/beans")
    public Result<Map<String, Map<String, Object>>> beans() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("userService", describe(userService));
        result.put("txDemoService", describe(txDemoService));
        result.put("userMapper", describe(userMapper));
        result.put("circularA", describe(circularA));
        return Result.ok(result);
    }

    /** 自动配置演示：GreetingService 不是我们写 @Bean 注册的，而是 GreetingAutoConfiguration 自动注册的 */
    @GetMapping("/greeting")
    public Result<String> greeting(@RequestParam(defaultValue = "Spring Boot") String name) {
        GreetingService service = greetingService.getIfAvailable();
        if (service == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "GreetingService 不存在：demo.greeting.enabled=false，自动配置没有生效");
        }
        return Result.ok(service.greet(name));
    }

    private Map<String, Object> describe(Object bean) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("class", bean.getClass().getName());
        info.put("isSpringAopProxy", AopUtils.isAopProxy(bean));
        info.put("isSpringCglibProxy", AopUtils.isCglibProxy(bean));
        info.put("isJdkProxyClass", Proxy.isProxyClass(bean.getClass()));
        return info;
    }
}
