package com.example.jvm.oom;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 直接内存溢出：java.lang.OutOfMemoryError: Cannot reserve ... bytes of direct buffer memory
 * <p>
 * ByteBuffer.allocateDirect 在堆外（本地内存）分配，NIO、Netty 大量使用，省去一次堆内外的数据拷贝。
 * 上限由 -XX:MaxDirectMemorySize 控制（默认约等于 -Xmx）。
 * <pre>-XX:MaxDirectMemorySize=10m</pre>
 * 堆外内存不在堆快照里，排查时 heap dump 看不出问题：要看 Native Memory Tracking（-XX:NativeMemoryTracking=summary + jcmd pid VM.native_memory）。
 * <p>
 * DirectByteBuffer 对象本身很小，在堆里；它背后的堆外内存靠 Cleaner（虚引用）在对象被回收时释放。
 * 如果堆一直很空闲不触发 GC，DirectByteBuffer 对象不被回收，堆外内存也就迟迟不释放。
 */
public class DirectMemoryOomDemo {

    public static void main(String[] args) {
        List<ByteBuffer> buffers = new ArrayList<>();
        try {
            while (true) {
                buffers.add(ByteBuffer.allocateDirect(1024 * 1024));
            }
        } catch (OutOfMemoryError e) {
            System.out.println("分配了 " + buffers.size() + " 个 1MB 的直接内存后：" + e.getMessage());
        }
    }
}
