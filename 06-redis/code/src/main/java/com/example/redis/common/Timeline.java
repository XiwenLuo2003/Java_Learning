package com.example.redis.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录多线程实验的时间线，返回给浏览器看“谁在第几毫秒做了什么”。
 */
public class Timeline {

    private final long start = System.currentTimeMillis();
    private final List<String> events = new ArrayList<>();

    public synchronized void log(String who, String what) {
        events.add(String.format("%5dms [%s] %s", System.currentTimeMillis() - start, who, what));
    }

    public synchronized List<String> events() {
        return new ArrayList<>(events);
    }
}
