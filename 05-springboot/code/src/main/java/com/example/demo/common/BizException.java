package com.example.demo.common;

import org.springframework.http.HttpStatus;

/**
 * 业务异常：可预期的错误（用户不存在、密码错误、未登录……），直接把 msg 返回给前端。
 * 继承 RuntimeException 有两个好处：业务代码不用到处 throws；@Transactional 默认就会因它回滚。
 */
public class BizException extends RuntimeException {

    private final HttpStatus status;

    public BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
