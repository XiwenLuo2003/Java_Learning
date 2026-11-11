package com.example.collection.arraylist;

import com.example.collection.Reflects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

/**
 * ArrayList 扩容机制 + fail-fast + 常见坑。
 * <p>
 * VM 参数：--add-opens java.base/java.util=ALL-UNNAMED（实验 1、2 要反射读 elementData）
 */
public class ArrayListDemo {

    /**
     * JDK 17 ArrayList#grow 的精简版（去掉了溢出检查）：
     * <pre>
     * private Object[] grow(int minCapacity) {
     *     int oldCapacity = elementData.length;
     *     if (oldCapacity &gt; 0 || elementData != DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
     *         int newCapacity = ArraysSupport.newLength(oldCapacity,
     *                 minCapacity - oldCapacity,   // 最少要长这么多
     *                 oldCapacity &gt;&gt; 1);          // 希望长这么多：0.5 倍
     *         return elementData = Arrays.copyOf(elementData, newCapacity);
     *     } else {
     *         // new ArrayList&lt;&gt;() 第一次 add：直接给 10
     *         return elementData = new Object[Math.max(DEFAULT_CAPACITY, minCapacity)];
     *     }
     * }
     * </pre>
     * ArraysSupport.newLength(old, minGrowth, prefGrowth) = old + max(minGrowth, prefGrowth)
     */
    static int grow(int oldCapacity, int minCapacity, boolean defaultEmpty) {
        if (oldCapacity > 0 || !defaultEmpty) {
            return oldCapacity + Math.max(minCapacity - oldCapacity, oldCapacity >> 1);
        }
        return Math.max(10, minCapacity);
    }

    public static void main(String[] args) {
        // ===== 实验 1：new ArrayList<>() 的扩容序列，预期 0 → 10 → 15 → 22 → 33 → 49 =====
        System.out.println("== 实验 1：无参构造 ==");
        List<Integer> list = new ArrayList<>();
        int last = Reflects.arrayListCapacity(list);
        System.out.println("刚创建，容量 = " + last + "（延迟分配，还没有数组）");
        for (int i = 1; i <= 50; i++) {
            list.add(i);
            int cap = Reflects.arrayListCapacity(list);
            if (cap != last) {
                System.out.printf("添加第 %2d 个元素时扩容：%d → %d（公式算出 %d）%n",
                        i, last, cap, grow(last, i, last == 0));
                last = cap;
            }
        }

        // ===== 实验 2：new ArrayList<>(0) 走的是另一个分支，预期 0 → 1 → 2 → 3 → 4 → 6 → 9 → 13 =====
        // 区别在于用的是 EMPTY_ELEMENTDATA 而不是 DEFAULTCAPACITY_EMPTY_ELEMENTDATA：你明确说了要 0，就不给你 10
        System.out.println("\n== 实验 2：new ArrayList<>(0) ==");
        List<Integer> zero = new ArrayList<>(0);
        last = 0;
        for (int i = 1; i <= 13; i++) {
            zero.add(i);
            int cap = Reflects.arrayListCapacity(zero);
            if (cap != last) {
                System.out.printf("第 %2d 个：%d → %d%n", i, last, cap);
                last = cap;
            }
        }

        // ===== 实验 3：fail-fast。foreach 里删除元素抛 ConcurrentModificationException =====
        // foreach 底层是 Iterator，每次 next() 检查 modCount == expectedModCount；list.remove 改了 modCount，迭代器的没变
        System.out.println("\n== 实验 3：fail-fast ==");
        List<String> names = new ArrayList<>(List.of("a", "b", "c", "d"));
        try {
            for (String name : names) {
                if (name.equals("b")) {
                    names.remove(name);
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("foreach 中 remove：抛出 " + e.getClass().getSimpleName());
        }
        // 特例：删倒数第二个元素不报错。删完 size 变小，hasNext() 判断 cursor != size 直接返回 false，循环提前结束，根本没走到检查
        List<String> tricky = new ArrayList<>(List.of("a", "b", "c"));
        for (String name : tricky) {
            if (name.equals("b")) {
                tricky.remove(name);
            }
        }
        System.out.println("删倒数第二个不报错，但 c 没被遍历到：" + tricky);

        // 正确写法 1：迭代器自己的 remove，会同步 expectedModCount
        Iterator<String> it = names.iterator();
        while (it.hasNext()) {
            if (it.next().equals("c")) {
                it.remove();
            }
        }
        // 正确写法 2：removeIf（JDK 8+，推荐）
        names.removeIf(name -> name.equals("d"));
        System.out.println("iterator.remove + removeIf 之后：" + names);

        // ===== 实验 4：Arrays.asList 和 List.of 都不能增删 =====
        System.out.println("\n== 实验 4：不可变 / 定长 List ==");
        List<String> fixed = Arrays.asList("x", "y");
        try {
            fixed.add("z");
        } catch (UnsupportedOperationException e) {
            // Arrays.asList 返回的是 Arrays 的内部类 ArrayList，没有重写 add，继承自 AbstractList 的 add 直接抛异常
            System.out.println("Arrays.asList().add：" + e.getClass().getSimpleName()
                    + "，实际类型是 " + fixed.getClass().getName());
        }
        String[] array = {"x", "y"};
        List<String> view = Arrays.asList(array);
        array[0] = "changed";
        System.out.println("Arrays.asList 是原数组的视图，改数组 list 也变：" + view);
        try {
            List.of("x").set(0, "y");
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of 完全不可变，连 set 都不行");
        }

        // ===== 实验 5：remove(int) 和 remove(Object) 的重载陷阱 =====
        System.out.println("\n== 实验 5：remove 重载 ==");
        List<Integer> numbers = new ArrayList<>(List.of(10, 20, 30, 1));
        numbers.remove(1);                    // 按下标删，删掉的是 20
        numbers.remove(Integer.valueOf(1));   // 按对象删，删掉的是元素 1
        System.out.println("预期 [10, 30]：" + numbers);
    }
}
