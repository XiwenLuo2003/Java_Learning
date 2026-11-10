package com.example.demo.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 切面 = 切点（在哪儿增强）+ 通知（增强什么）。
 * <ul>
 *   <li>切点：{@code @annotation(logExecution)}，匹配所有贴了 @LogExecution 的方法</li>
 *   <li>通知：@Around 环绕通知，能控制目标方法是否执行、拿到返回值和异常，是最强大的一种</li>
 * </ul>
 * Spring 启动时，AnnotationAwareAspectJAutoProxyCreator（一个 BeanPostProcessor）会在 Bean 初始化之后检查：
 * 这个 Bean 有没有方法匹配某个切点？有就用 CGLIB 生成代理对象，放进容器的就是代理而不是原对象。
 * 访问 GET /demo/beans 可以看到 UserService 的真实类型是 UserService$$SpringCGLIB$$0。
 * <p>
 * 调用登录接口后，控制台会看到：
 * <pre>
 * [AOP] 开始 用户登录 UserService.login(..) 参数=[LoginRequest[username=admin, password=123456]]
 * [AOP] 结束 用户登录 UserService.login(..) 耗时=xxms
 * </pre>
 */
@Aspect
@Component
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    @Around("@annotation(logExecution)")
    public Object around(ProceedingJoinPoint joinPoint, LogExecution logExecution) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        log.info("[AOP] 开始 {} {} 参数={}", logExecution.value(), method, Arrays.toString(joinPoint.getArgs()));
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("[AOP] 结束 {} {} 耗时={}ms", logExecution.value(), method, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            // 只记录不吞掉，异常继续往上抛给全局异常处理；吞掉的话 @Transactional 也感知不到异常
            log.warn("[AOP] 异常 {} {} 耗时={}ms 异常={}", logExecution.value(), method,
                    System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }
}
