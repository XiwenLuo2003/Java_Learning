package com.example.jvm.memory;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.vm.VM;

/**
 * 对象在堆里长什么样：对象头 + 实例数据 + 对齐填充。
 * <pre>
 * ┌──────────────── 对象头 ────────────────┐
 * │ Mark Word（8 字节）：hashCode、GC 分代年龄、锁状态 │
 * │ 类型指针 Klass Pointer（开启压缩时 4 字节）：指向方法区里的类元数据 │
 * │ 数组长度（4 字节，只有数组才有）                  │
 * ├──────────────── 实例数据 ──────────────┤
 * │ 字段，JVM 会重排顺序：long/double → int/float → short/char → byte/boolean → 引用 │
 * ├──────────────── 对齐填充 ──────────────┤
 * │ 补齐到 8 字节的整数倍                            │
 * └─────────────────────────────────────────┘
 * </pre>
 * 可以加 VM 参数 -XX:-UseCompressedOops -XX:-UseCompressedClassPointers 关闭指针压缩，对比对象大小的变化。
 */
public class ObjectLayoutDemo {

    static class User {
        boolean active;
        int age;
        long id;
        String name;
    }

    public static void main(String[] args) {
        // JVM 信息：是否开启指针压缩、对象对齐字节数等
        System.out.println(VM.current().details());

        // new Object()：12 字节对象头 + 4 字节填充 = 16 字节
        System.out.println(ClassLayout.parseInstance(new Object()).toPrintable());

        // 字段被重排：long 在前，boolean 被塞进对象头后面的空隙里
        System.out.println(ClassLayout.parseInstance(new User()).toPrintable());

        // 数组：对象头多 4 字节的长度字段
        System.out.println(ClassLayout.parseInstance(new int[3]).toPrintable());

        // Mark Word 里的分代年龄（输出里的 age）只占 4 位，最大 15，所以 -XX:MaxTenuringThreshold 最大只能设 15
        System.out.println("看上面输出的 (object header: mark) 一行，括号里的 age: 0 就是分代年龄");
    }
}
