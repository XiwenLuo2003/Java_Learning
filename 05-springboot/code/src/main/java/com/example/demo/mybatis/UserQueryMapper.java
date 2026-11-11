package com.example.demo.mybatis;

import com.example.demo.mybatis.dto.UserSearchQuery;
import com.example.demo.mybatis.dto.UserWithOrders;
import com.example.demo.user.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 手写 SQL 的 Mapper（对比 user/UserMapper 继承 BaseMapper 的写法）。
 * <p>
 * 接口和 XML 是怎么对上的：XML 的 namespace = 本接口的全限定名，每个 &lt;select&gt; 的 id = 方法名。
 * MyBatis 启动时把每条 SQL 解析成一个 MappedStatement，key 是 “namespace.id”，
 * 调用接口方法时，MapperProxy（JDK 动态代理）用 “接口全名.方法名” 找到对应的 SQL 执行。
 * 所以 Mapper 接口里的方法<b>不能重载</b>：同名方法会对应同一个 id。
 * <p>
 * SQL 全部写在 resources/mapper/UserQueryMapper.xml 里。
 */
@Mapper
public interface UserQueryMapper {

    /** 动态 SQL：&lt;where&gt; + &lt;if&gt; + &lt;choose&gt; */
    List<User> search(UserSearchQuery query);

    /** 动态 SQL：&lt;foreach&gt; 拼 IN 列表。多个参数或集合参数要用 @Param 起名，XML 里才能按名字引用 */
    List<User> selectByIds(@Param("ids") List<Long> ids);

    /** #{}：预编译占位符，安全 */
    List<User> findByNameSafe(@Param("name") String name);

    /** ${}：字符串直接拼进 SQL，有 SQL 注入风险，仅用于演示 */
    List<User> findByNameUnsafe(@Param("name") String name);

    /** 一对多，JOIN 一次查出来，用 resultMap 的 &lt;collection&gt; 组装 */
    UserWithOrders selectWithOrders(@Param("id") Long id);

    /** 一对多，嵌套查询写法：先查用户，再为每个用户各查一次订单，会产生 N+1 问题 */
    List<UserWithOrders> selectAllWithOrdersNPlusOne();

    /** 一对多，JOIN 写法：一条 SQL 查出全部用户和订单，对比上面的 N+1 */
    List<UserWithOrders> selectAllWithOrdersJoin();
}
