package com.example.jvm.oom;

/**
 * 虚拟机栈溢出：java.lang.StackOverflowError
 * <p>
 * 每次方法调用都会在当前线程的虚拟机栈里压入一个栈帧（局部变量表、操作数栈、动态链接、返回地址），
 * 递归太深，栈空间用完就抛 StackOverflowError。
 * <p>
 * 先不加参数运行一次，再加 VM 参数 -Xss256k 运行一次（-Xss 设置每个线程的栈大小，默认 1MB 左右），对比深度。
 * 典型线上原因：递归没有正确的终止条件、两个对象的 toString / equals 互相调用、JSON 序列化循环引用。
 */
public class StackOverflowDemo {

    private static int depth = 0;

    /** 局部变量很少的递归 */
    static void small() {
        depth++;
        small();
    }

    /** 局部变量多，栈帧更大，同样的栈空间能递归的深度更浅 */
    static void big() {
        long a = 1, b = 2, c = 3, d = 4, e = 5, f = 6, g = 7, h = 8;
        depth++;
        big();
        depth += (int) (a + b + c + d + e + f + g + h);   // 让局部变量“有用”，防止被优化掉
    }

    public static void main(String[] args) {
        try {
            small();
        } catch (StackOverflowError e) {
            System.out.println("局部变量少：递归深度 " + depth);
        }
        depth = 0;
        try {
            big();
        } catch (StackOverflowError e) {
            System.out.println("局部变量多：递归深度 " + depth + "（栈帧越大，深度越浅）");
        }
        System.out.println("加 -Xss256k 再跑一次，两个深度都会明显变小。注意：每次运行的深度会有波动（JIT 编译前后栈帧大小不同）");
    }
}
