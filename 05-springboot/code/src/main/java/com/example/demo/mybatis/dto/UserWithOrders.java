package com.example.demo.mybatis.dto;

import java.util.ArrayList;
import java.util.List;

/** 一对多：一个用户 + 他的全部订单，由 UserQueryMapper.xml 里的 resultMap 组装 */
public class UserWithOrders {

    private Long id;
    private String username;
    private String nickname;
    private List<OrderItem> orders = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public List<OrderItem> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderItem> orders) {
        this.orders = orders;
    }
}
