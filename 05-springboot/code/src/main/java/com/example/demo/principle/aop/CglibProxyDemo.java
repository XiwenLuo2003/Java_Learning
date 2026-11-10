package com.example.demo.principle.aop;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

import java.lang.reflect.Method;

/**
 * 第 3 课（下）：CGLIB 动态代理，以及 Spring AOP 为什么会“自调用失效”。
 * <p>
 * CGLIB 在运行时生成目标类的<b>子类</b>，重写父类方法来插入增强逻辑，所以目标类不需要实现接口。
 * 限制：final 类无法被继承，final / private / static 方法无法被重写，这些方法都增强不了。
 * Spring Boot 2.x 起默认用 CGLIB（spring.aop.proxy-target-class=true）。CGLIB 已被 Spring 内置在
 * org.springframework.cglib 包下，所以这里不用额外引依赖。
 * <p>
 * 这里对比两种写法：
 * <ul>
 *   <li>方式 A：invokeSuper，代理对象自己就是业务对象（子类），内部 this 调用也会被拦截</li>
 *   <li>方式 B：Spring AOP 的做法，代理对象只负责增强，再转调一个独立的 target 对象，内部 this 是 target，不会被拦截</li>
 * </ul>
 * 预期输出：
 * <pre>
 * 代理类: ...OrderService$$EnhancerByCGLIB$$xxxx（名字可能略有不同）
 * 代理对象是 OrderService 吗？ true
 * ======== 方式 A：invokeSuper（内部调用也被拦截） ========
 * [A 拦截] placeOrder
 *   下单
 * [A 拦截] pay
 *   支付
 * ======== 方式 B：Spring AOP 风格（代理 + 独立 target） ========
 * [B 拦截] placeOrder
 *   下单
 *   支付                     &lt;- 内部 this.pay() 没被拦截，这就是 @Transactional 自调用失效
 * ======== final 方法无法被重写，所以不会被拦截 ========
 *   取消订单
 * </pre>
 */
public class CglibProxyDemo {

    public static class OrderService {
        public void placeOrder() {
            System.out.println("  下单");
            this.pay();
        }

        public void pay() {
            System.out.println("  支付");
        }

        public final void cancel() {
            System.out.println("  取消订单");
        }
    }

    public static void main(String[] args) {
        Enhancer enhancerA = new Enhancer();
        enhancerA.setSuperclass(OrderService.class);
        enhancerA.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) -> {
            System.out.println("[A 拦截] " + method.getName());
            return methodProxy.invokeSuper(obj, methodArgs);
        });
        OrderService proxyA = (OrderService) enhancerA.create();

        OrderService target = new OrderService();
        Enhancer enhancerB = new Enhancer();
        enhancerB.setSuperclass(OrderService.class);
        enhancerB.setCallback((MethodInterceptor) (obj, method, methodArgs, methodProxy) -> {
            System.out.println("[B 拦截] " + method.getName());
            return invokeTarget(target, method, methodArgs);
        });
        OrderService proxyB = (OrderService) enhancerB.create();

        System.out.println("代理类: " + proxyA.getClass().getName());
        System.out.println("代理对象是 OrderService 吗？ " + (proxyA instanceof OrderService));

        System.out.println("======== 方式 A：invokeSuper（内部调用也被拦截） ========");
        proxyA.placeOrder();

        System.out.println("======== 方式 B：Spring AOP 风格（代理 + 独立 target） ========");
        proxyB.placeOrder();

        System.out.println("======== final 方法无法被重写，所以不会被拦截 ========");
        proxyA.cancel();
    }

    private static Object invokeTarget(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
