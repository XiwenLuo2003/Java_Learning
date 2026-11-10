package com.example.demo.common;

/**
 * 统一返回体：所有接口都返回 {code, msg, data}，前端只需要按 code 判断成功与否。
 * code = 0 表示成功，其他值和 HTTP 状态码保持一致，方便排查。
 */
public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
