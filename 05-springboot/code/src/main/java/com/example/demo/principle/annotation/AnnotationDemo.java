package com.example.demo.principle.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 第 1 课：注解的本质。
 * <p>
 * 注解只是贴在代码上的“标签”（元数据），它自己不做任何事。
 * 必须有一段代码用反射把它读出来，再决定做什么 —— 这段代码就叫“注解处理器”。
 * Spring 的 @Component、@Autowired、@Transactional 全是这样：Spring 启动时扫描、读取注解，再去创建对象、注入依赖、生成代理。
 * <p>
 * 直接运行 main，预期输出：
 * <pre>
 * checkout -> 限流 qps=10, 需要登录=true
 * ping     -> 没有 @RateLimit，什么都不会发生
 * query    -> 限流 qps=100, 需要登录=false
 * </pre>
 * 动手试试：把 {@code RetentionPolicy.RUNTIME} 改成 {@code CLASS}，再运行，三个方法都读不到注解了。
 */
public class AnnotationDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface RateLimit {
        int qps();

        boolean requireLogin() default true;
    }

    static class OrderApi {
        @RateLimit(qps = 10)
        public void checkout() {
        }

        @RateLimit(qps = 100, requireLogin = false)
        public void query() {
        }

        public void ping() {
        }
    }

    public static void main(String[] args) {
        // getDeclaredMethods() 不保证顺序，排序只是为了输出稳定
        Method[] methods = OrderApi.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        for (Method method : methods) {
            RateLimit rateLimit = method.getAnnotation(RateLimit.class);
            if (rateLimit == null) {
                System.out.printf("%-8s -> 没有 @RateLimit，什么都不会发生%n", method.getName());
            } else {
                System.out.printf("%-8s -> 限流 qps=%d, 需要登录=%b%n",
                        method.getName(), rateLimit.qps(), rateLimit.requireLogin());
            }
        }
    }
}
