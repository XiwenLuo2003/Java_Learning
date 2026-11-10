package com.example.demo.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 字段为 null 表示不修改（MyBatis-Plus 的 updateById 默认跳过 null 字段） */
public record UserUpdateRequest(
        String nickname,

        @Min(value = 0, message = "年龄不能小于 0")
        @Max(value = 150, message = "年龄不能大于 150")
        Integer age) {
}
