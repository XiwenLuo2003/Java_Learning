package com.example.demo.web;

/**
 * 当前登录用户上下文。Tomcat 一个请求由一个线程处理到底，所以用 ThreadLocal 在拦截器里存、在 Service 里取，
 * 不用把 userId 一层层当参数传。
 * <p>
 * 必须在请求结束时 clear（见 LoginInterceptor#afterCompletion）：Tomcat 线程是池化复用的，不清理的话
 * 下一个请求复用这个线程时会读到上一个用户的 id（串号），同时 value 也一直无法回收（ThreadLocal 内存泄漏）。
 * <p>
 * 另一个坑：@Async、线程池里的子线程拿不到这里的值，要手动传参。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
