package com.example.demo.user.dto;

import com.example.demo.user.User;

import java.time.LocalDateTime;

/** 返回给前端的用户信息，不含 password */
public record UserVO(Long id, String username, String nickname, Integer age, LocalDateTime createTime) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAge(), user.getCreateTime());
    }
}
