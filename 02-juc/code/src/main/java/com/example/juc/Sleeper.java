package com.example.juc;

/** 演示代码里到处要 sleep，把受检异常收起来，让 Demo 主体更好读 */
public final class Sleeper {

    private Sleeper() {
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
