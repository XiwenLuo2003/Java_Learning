package com.example.demo.principle.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 第 3 课（上）：AOP 的底层 —— JDK 动态代理。
 * <p>
 * AOP 想做的事：不改业务代码，在方法前后“织入”日志、事务、权限等通用逻辑。
 * 实现手段就是代理：调用方拿到的不是原对象，而是一个代理对象，代理先执行增强逻辑，再转调原对象（target）。
 * <p>
 * JDK 动态代理的限制：目标类必须实现接口，代理对象只实现了这个接口，所以它不能强转成实现类。
 * <p>
 * 预期输出（耗时数字每次不同）：
 * <pre>
 * 代理类: jdk.proxy1.$Proxy0（每次运行名字可能略有不同）
 * 代理对象是 UserService 吗？ true
 * 代理对象是 UserServiceImpl 吗？ false
 * ---- 1. 直接调用 register ----
 * [前置增强] register
 *   写库: 张三
 * [后置增强] register 耗时 xx μs
 * ---- 2. 调用 registerBatch（内部 this.register 两次） ----
 * [前置增强] registerBatch
 *   写库: 李四
 *   写库: 王五
 * [后置增强] registerBatch 耗时 xx μs
 * </pre>
 * 重点看第 2 段：内部的两次 register 没有被增强！因为 registerBatch 里的 this 是原对象，不是代理。
 * 这就是 @Transactional / @Async / 自定义切面“自调用失效”的根本原因。
 */
public class JdkProxyDemo {

    interface UserService {
        void register(String name);

        void registerBatch(List<String> names);
    }

    static class UserServiceImpl implements UserService {
        @Override
        public void register(String name) {
            System.out.println("  写库: " + name);
        }

        @Override
        public void registerBatch(List<String> names) {
            for (String name : names) {
                this.register(name);
            }
        }
    }

    /** 相当于 Spring 的切面：所有方法调用都会先进到 invoke */
    static class TimingHandler implements InvocationHandler {
        private final Object target;

        TimingHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long start = System.nanoTime();
            System.out.println("[前置增强] " + method.getName());
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                // 反射会把业务异常包一层，必须拆出来再抛，否则调用方拿到的异常类型就变了
                throw e.getTargetException();
            } finally {
                System.out.printf("[后置增强] %s 耗时 %d μs%n", method.getName(), (System.nanoTime() - start) / 1000);
            }
        }
    }

    public static void main(String[] args) {
        UserService target = new UserServiceImpl();
        UserService proxy = (UserService) Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class<?>[]{UserService.class},
                new TimingHandler(target));

        System.out.println("代理类: " + proxy.getClass().getName());
        System.out.println("代理对象是 UserService 吗？ " + (proxy instanceof UserService));
        System.out.println("代理对象是 UserServiceImpl 吗？ " + (proxy instanceof UserServiceImpl));

        System.out.println("---- 1. 直接调用 register ----");
        proxy.register("张三");

        System.out.println("---- 2. 调用 registerBatch（内部 this.register 两次） ----");
        proxy.registerBatch(List.of("李四", "王五"));
    }
}
