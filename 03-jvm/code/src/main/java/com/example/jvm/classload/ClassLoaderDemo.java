package com.example.jvm.classload;

import java.io.IOException;
import java.io.InputStream;

/**
 * 类加载器层级、双亲委派、打破双亲委派。
 * <p>
 * JDK 9+ 的三层类加载器：
 * <pre>
 * 启动类加载器 Bootstrap（C++ 实现，Java 里拿到的是 null）  加载 java.base 等核心模块：java.lang.String
 *        ▲ parent
 * 平台类加载器 Platform（JDK 8 叫扩展类加载器 Extension）     加载 java.sql 等平台模块
 *        ▲ parent
 * 应用类加载器 App（也叫系统类加载器）                          加载 classpath 上我们自己写的类
 * </pre>
 * 双亲委派（ClassLoader#loadClass 源码）：先查自己加载过没有 → 没有就交给 parent.loadClass → 父加载器找不到，
 * 才调用自己的 findClass。好处：核心类只会被启动类加载器加载，自己写一个 java.lang.String 也替换不了它，保证安全；
 * 同一个类不会被重复加载。
 */
public class ClassLoaderDemo {

    /** 被隔离加载的类 */
    public static class Sample {
    }

    /**
     * 打破双亲委派：重写 loadClass，对 Sample 不先问父加载器，自己读字节码 defineClass。
     * Tomcat 就是这么做的：每个 Web 应用一个 WebappClassLoader，优先加载自己 WEB-INF 下的类，
     * 这样两个应用可以用同一个类的不同版本，互不干扰。
     */
    static class IsolatedLoader extends ClassLoader {
        IsolatedLoader() {
            super(ClassLoaderDemo.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(Sample.class.getName())) {
                return super.loadClass(name, resolve);   // 其他类照常走双亲委派
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    byte[] bytes = readClassBytes(name);
                    c = defineClass(name, bytes, 0, bytes.length);
                }
                return c;
            }
        }

        Class<?> defineRaw(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    static byte[] readClassBytes(String name) {
        try (InputStream in = ClassLoaderDemo.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        // ===== 实验 1：类加载器层级 =====
        System.out.println("== 实验 1：类加载器层级 ==");
        System.out.println("String       → " + String.class.getClassLoader() + "（null 表示启动类加载器）");
        System.out.println("java.sql.Date → " + java.sql.Date.class.getClassLoader().getName());
        ClassLoader app = ClassLoaderDemo.class.getClassLoader();
        System.out.println("ClassLoaderDemo → " + app.getName());
        System.out.println("app 的 parent → " + app.getParent().getName() + "，platform 的 parent → " + app.getParent().getParent());

        // ===== 实验 2：双亲委派。随便 new 一个类加载器去加载 String，拿到的还是启动类加载器加载的那一个 =====
        System.out.println("\n== 实验 2：双亲委派 ==");
        ClassLoader custom = new ClassLoader(app) {
        };
        Class<?> s = custom.loadClass("java.lang.String");
        System.out.println("自定义加载器加载 java.lang.String，是同一个 Class 吗？" + (s == String.class));

        // 想直接 define 一个 java.* 包下的类会被拒绝（ClassLoader#preDefineClass 里检查包名）
        try {
            new IsolatedLoader().defineRaw("java.lang.Hacked", readClassBytes(Sample.class.getName()));
        } catch (SecurityException e) {
            System.out.println("define java.lang.Hacked：SecurityException —— " + e.getMessage());
        }

        // ===== 实验 3：打破双亲委派。同一份字节码被两个加载器加载，是两个不同的类 =====
        System.out.println("\n== 实验 3：打破双亲委派 ==");
        Class<?> isolated = new IsolatedLoader().loadClass(Sample.class.getName());
        Object instance = isolated.getDeclaredConstructor().newInstance();
        System.out.println("类名相同吗？" + isolated.getName().equals(Sample.class.getName()));
        System.out.println("是同一个 Class 吗？" + (isolated == Sample.class));
        System.out.println("instance instanceof Sample？" + (instance instanceof Sample)
                + " —— 类的唯一性由“类加载器 + 全限定名”共同决定");
        try {
            Sample casted = (Sample) instance;
            System.out.println(casted);
        } catch (ClassCastException e) {
            System.out.println("强转报 ClassCastException：" + e.getMessage());
        }
    }
}
