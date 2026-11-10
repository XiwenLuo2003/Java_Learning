package com.example.demo.principle.ioc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 第 2 课：手写一个迷你 IoC 容器（约 60 行核心代码），看清 Spring 容器替你做了什么。
 * <p>
 * IoC（控制反转）：对象不再自己 new 依赖，而是交给容器创建和组装。
 * DI（依赖注入）：容器把依赖“塞”进对象，这里用的是字段注入，对应 Spring 的 @Autowired 字段注入。
 * <p>
 * 这个容器做了 Spring 最核心的三件事：
 * <ol>
 *   <li>扫描：只管理贴了 @MyComponent 的类（对应 @ComponentScan + @Component）</li>
 *   <li>实例化：反射调用无参构造器，并且是单例（对应 Spring 默认的 singleton 作用域）</li>
 *   <li>属性注入：找到 @MyAutowired 字段，递归 getBean 后反射赋值（对应 populateBean）</li>
 * </ol>
 * 另外用 earlySingletonObjects 提前暴露“半成品”对象，解决了 OrderService ⇄ PayService 的循环依赖，
 * 这就是 Spring 三级缓存的简化版（Spring 多一级 singletonFactories，用来处理 AOP 代理，见八股）。
 * <p>
 * 预期输出：
 * <pre>
 * [实例化] OrderService
 * [属性注入] OrderService.userDao
 * [实例化] UserDao
 * [初始化完成] UserDao
 * [属性注入] OrderService.payService
 * [实例化] PayService
 * [属性注入] PayService.orderService   &lt;- 此时 OrderService 还没初始化完，从 earlySingletonObjects 拿到半成品
 * [初始化完成] PayService
 * [初始化完成] OrderService
 * 下单: 查询用户 1001 -> 支付成功
 * 循环依赖拿到的是同一个对象吗？ true
 * 是单例吗？ true
 * PlainService 没有 @MyComponent: 容器里没有这个 Bean: PlainService
 * </pre>
 */
public class MiniIocDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface MyComponent {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface MyAutowired {
    }

    static class MiniContainer {
        /** 一级缓存：完整的 Bean（实例化 + 注入都做完） */
        private final Map<Class<?>, Object> singletonObjects = new HashMap<>();
        /** 二级缓存：已实例化但还没注入完的“半成品”，专门用来打破循环依赖 */
        private final Map<Class<?>, Object> earlySingletonObjects = new HashMap<>();
        private final Set<Class<?>> beanClasses;

        MiniContainer(Class<?>... candidates) {
            // 第 1 步：扫描。注解就是在这里被“读”的，没被读到的注解毫无作用
            this.beanClasses = Arrays.stream(candidates)
                    .filter(c -> c.isAnnotationPresent(MyComponent.class))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // 和 Spring 一样，启动时就把所有单例创建好（非懒加载）
            for (Class<?> beanClass : beanClasses) {
                getBean(beanClass);
            }
        }

        <T> T getBean(Class<T> type) {
            Object bean = singletonObjects.get(type);
            if (bean == null) {
                bean = earlySingletonObjects.get(type);
            }
            if (bean != null) {
                return type.cast(bean);
            }
            if (!beanClasses.contains(type)) {
                throw new IllegalStateException("容器里没有这个 Bean: " + type.getSimpleName());
            }
            return type.cast(createBean(type));
        }

        private Object createBean(Class<?> type) {
            try {
                // 第 2 步：实例化
                Object instance = type.getDeclaredConstructor().newInstance();
                System.out.println("[实例化] " + type.getSimpleName());
                earlySingletonObjects.put(type, instance);

                // 第 3 步：属性注入
                for (Field field : type.getDeclaredFields()) {
                    if (field.isAnnotationPresent(MyAutowired.class)) {
                        System.out.println("[属性注入] " + type.getSimpleName() + "." + field.getName());
                        field.setAccessible(true);
                        field.set(instance, getBean(field.getType()));
                    }
                }

                earlySingletonObjects.remove(type);
                singletonObjects.put(type, instance);
                System.out.println("[初始化完成] " + type.getSimpleName());
                return instance;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("创建 Bean 失败: " + type.getSimpleName(), e);
            }
        }
    }

    // ---------------- 下面是“业务代码”，注意它们都没有 new 任何依赖 ----------------

    @MyComponent
    static class UserDao {
        String findName(long id) {
            return "查询用户 " + id;
        }
    }

    @MyComponent
    static class OrderService {
        @MyAutowired
        UserDao userDao;
        @MyAutowired
        PayService payService;

        void createOrder() {
            System.out.println("下单: " + userDao.findName(1001) + " -> " + payService.pay());
        }
    }

    @MyComponent
    static class PayService {
        @MyAutowired
        OrderService orderService;

        String pay() {
            return "支付成功";
        }
    }

    static class PlainService {
    }

    public static void main(String[] args) {
        MiniContainer container = new MiniContainer(
                OrderService.class, UserDao.class, PayService.class, PlainService.class);

        OrderService orderService = container.getBean(OrderService.class);
        orderService.createOrder();

        System.out.println("循环依赖拿到的是同一个对象吗？ " + (orderService.payService.orderService == orderService));
        System.out.println("是单例吗？ " + (container.getBean(OrderService.class) == orderService));

        try {
            container.getBean(PlainService.class);
        } catch (IllegalStateException e) {
            System.out.println("PlainService 没有 @MyComponent: " + e.getMessage());
        }
    }
}
