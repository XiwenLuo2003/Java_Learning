package com.example.jvm.oom;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 元空间溢出：java.lang.OutOfMemoryError: Metaspace
 * <p>
 * 元空间（JDK 8 起替代永久代）存放类的元数据：类结构、方法、字段、运行时常量池等，使用本地内存而不是堆。
 * 默认没有上限（受物理内存限制），这里用 VM 参数限制住：
 * <pre>-XX:MaxMetaspaceSize=32m</pre>
 * <p>
 * 复现方法：同一个类被不同的 ClassLoader 加载，在 JVM 看来就是不同的类（类的唯一性 = 类加载器 + 全限定名），
 * 每加载一次就多一份元数据。把 ClassLoader 都存起来不让它们被回收，元空间就会被撑爆。
 * <p>
 * 典型线上原因：CGLIB / 动态代理 / Groovy 脚本 / 反射框架运行时不断生成新类，且生成类的 ClassLoader 没释放。
 */
public class MetaspaceOomDemo {

    /** 被反复加载的类 */
    public static class Victim {
        public int a;
        public long b;

        public String hello() {
            return "hello";
        }
    }

    /** 不委派给父加载器，自己 defineClass，所以每个实例都会得到一个新的 Class */
    static class OneShotLoader extends ClassLoader {
        OneShotLoader() {
            super(null);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws IOException {
        String name = Victim.class.getName();
        byte[] bytes;
        try (InputStream in = MetaspaceOomDemo.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            bytes = in.readAllBytes();
        }
        List<Class<?>> keep = new ArrayList<>();   // 持有 Class → 持有它的 ClassLoader → 元数据无法卸载
        try {
            while (true) {
                keep.add(new OneShotLoader().define(name, bytes));
                if (keep.size() % 5000 == 0) {
                    System.out.println("已加载 " + keep.size() + " 个同名的类");
                }
            }
        } catch (OutOfMemoryError e) {
            int size = keep.size();
            keep.clear();
            System.out.println("加载了 " + size + " 次后：" + e);
        }
    }
}
