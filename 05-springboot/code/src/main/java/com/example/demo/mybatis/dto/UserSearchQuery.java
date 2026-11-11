package com.example.demo.mybatis.dto;

/**
 * 多条件查询参数。同时演示两件事：
 * <ul>
 *   <li>Spring MVC：Controller 参数直接写这个类型，查询参数按字段名自动绑定（需要 setter）</li>
 *   <li>MyBatis：XML 里用 #{username}、test="minAge != null" 读取字段（通过 getter）</li>
 * </ul>
 * 用普通类而不是 record：MyBatis 的 OGNL 表达式和 Spring 的数据绑定都按 JavaBean 的 getter / setter 工作。
 */
public class UserSearchQuery {

    private String username;
    private Integer minAge;
    private Integer maxAge;
    /** 排序字段，只允许 id、age，见 UserQueryMapper.xml 的 &lt;choose&gt; */
    private String orderBy;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }
}
