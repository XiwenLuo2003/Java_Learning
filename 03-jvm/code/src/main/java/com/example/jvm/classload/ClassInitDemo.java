package com.example.jvm.classload;

/**
 * 类的生命周期：加载 → 验证 → 准备 → 解析 → 初始化 → 使用 → 卸载。
 * <ul>
 *   <li>准备：给静态变量分配内存并设为零值（int 是 0，引用是 null）；static final 的编译期常量在这一步就直接赋值</li>
 *   <li>初始化：执行 &lt;clinit&gt;，也就是按代码顺序执行静态变量赋值和 static 块；父类的 &lt;clinit&gt; 先执行</li>
 * </ul>
 * 只有“主动使用”才会触发初始化：new、读写静态字段（编译期常量除外）、调用静态方法、反射、初始化子类、main 所在类。
 * 下面几种“被动引用”不会触发初始化。每个类只初始化一次，所以实验顺序是精心安排过的。
 */
public class ClassInitDemo {

    static class Parent {
        static final String CONSTANT = "编译期常量";
        static int parentValue = 1;

        static {
            System.out.println("    [Parent 静态代码块]");
        }

        {
            System.out.println("    [Parent 实例代码块]");
        }

        Parent() {
            System.out.println("    [Parent 构造器]");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("    [Child 静态代码块]");
        }

        {
            System.out.println("    [Child 实例代码块]");
        }

        Child() {
            System.out.println("    [Child 构造器]");
        }
    }

    public static void main(String[] args) {
        System.out.println("1. 读取 Parent.CONSTANT：编译时已经被内联成字面量，不会触发任何类的初始化");
        System.out.println("   值 = " + Parent.CONSTANT);

        System.out.println("2. new Parent[3]：创建数组不会初始化 Parent（初始化的是 JVM 生成的数组类）");
        Parent[] array = new Parent[3];
        System.out.println("   数组长度 = " + array.length);

        System.out.println("3. 通过子类读父类的静态字段 Child.parentValue：只初始化 Parent，不初始化 Child");
        System.out.println("   值 = " + Child.parentValue);

        System.out.println("4. new Child()：Parent 已经初始化过，只执行 Child 的静态块，然后是实例的初始化顺序");
        new Child();

        System.out.println("5. 再 new Child()：静态块不会再执行，只有实例部分");
        new Child();
    }
}
