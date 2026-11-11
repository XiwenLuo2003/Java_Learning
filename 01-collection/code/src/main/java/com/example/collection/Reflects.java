package com.example.collection;

import java.lang.reflect.Field;

/**
 * 用反射读取 JDK 集合类的私有字段（elementData、table），把源码里的行为“看见”。
 * <p>
 * JDK 9 起模块系统默认不允许反射访问 java.util 的私有字段，运行时需要加 VM 参数：
 * <pre>--add-opens java.base/java.util=ALL-UNNAMED</pre>
 * IDEA：Run → Edit Configurations → Modify options → Add VM options。
 */
public final class Reflects {

    private Reflects() {
    }

    public static Object field(Object target, String name) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (RuntimeException | IllegalAccessException e) {
                throw new IllegalStateException(
                        "反射失败，请加 VM 参数：--add-opens java.base/java.util=ALL-UNNAMED", e);
            }
        }
        throw new IllegalArgumentException(target.getClass() + " 没有字段 " + name);
    }

    /** ArrayList 底层数组的长度，即容量 */
    public static int arrayListCapacity(Object list) {
        return ((Object[]) field(list, "elementData")).length;
    }

    /** HashMap 底层 table 的长度，未初始化时返回 0 */
    public static int hashMapCapacity(Object map) {
        Object[] table = (Object[]) field(map, "table");
        return table == null ? 0 : table.length;
    }

    /** HashMap 某个桶的头节点类型：Node 是链表，TreeNode 是红黑树 */
    public static String binType(Object map, int index) {
        Object[] table = (Object[]) field(map, "table");
        if (table == null || table[index] == null) {
            return "空";
        }
        return table[index].getClass().getSimpleName();
    }
}
