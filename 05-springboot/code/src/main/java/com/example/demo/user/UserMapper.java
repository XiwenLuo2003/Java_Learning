package com.example.demo.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 只有接口没有实现类，却能被注入使用：MyBatis 启动时为每个 @Mapper 接口生成 JDK 动态代理（MapperProxy），
 * 调用方法时由代理去执行对应的 SQL —— 又是第 3 课的动态代理。
 * BaseMapper 已经提供了 selectById、insert、updateById、deleteById、selectPage 等单表 CRUD。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
