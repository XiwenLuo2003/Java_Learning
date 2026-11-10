package com.example.demo.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 校验注解同样只是元数据：Controller 参数上加了 @Valid，Spring MVC 才会调用 Hibernate Validator 去读它们，
 * 校验失败抛 MethodArgumentNotValidException，由 GlobalExceptionHandler 统一转成 400。
 */
public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度 3~20")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度 6~32")
        String password,

        String nickname,

        @Min(value = 0, message = "年龄不能小于 0")
        @Max(value = 150, message = "年龄不能大于 150")
        Integer age) {
}
