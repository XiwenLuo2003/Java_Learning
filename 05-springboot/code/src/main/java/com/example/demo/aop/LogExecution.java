package com.example.demo.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义注解：贴在方法上，表示“记录这个方法的入参、耗时和异常”。
 * 和第 1 课一样，它自己什么都不做，真正干活的是 {@link LogAspect}。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogExecution {

    /** 业务描述，打印在日志里 */
    String value() default "";
}
