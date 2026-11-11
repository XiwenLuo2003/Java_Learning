# Spring Boot 学习项目

一个 Maven 项目覆盖 [八股.md](../八股.md) 里的全部知识点：前 3 课是纯 Java 原理拆解，后面在 Spring Boot 里逐个实操，最后落到一个带 JWT 登录的用户管理系统（路线 Day 22-28 的“行动”）。

技术栈：Spring Boot 3.3 + MyBatis-Plus 3.5 + H2（MySQL 兼容模式，内存库，免安装）+ jjwt 0.12。

## 运行

1. IDEA → Open → 选择 `05-springboot/code` 目录，按 Maven 项目导入。
2. 项目 SDK 需要 **JDK 17+**（File → Project Structure → SDK，IDEA 里可以直接下载）。
3. 原理课（第 1~3 课）：直接右键运行对应类的 `main`。
4. Spring Boot 部分：运行 `DemoApplication`，用 [requests.http](./requests.http) 逐个发请求。
5. H2 控制台：<http://localhost:8080/h2-console>，JDBC URL 填 `jdbc:h2:mem:demo`，用户名 `sa`，密码留空。

预置账号：`admin / 123456`（另有 zhangsan、lisi、wangwu、zhaoliu，密码相同）。

## 学习顺序

| 课 | 知识点 | 代码 | 怎么看效果 |
|---|---|---|---|
| 1 | 注解的本质 | `principle/annotation/AnnotationDemo` | 运行 main |
| 2 | IoC / DI、循环依赖雏形 | `principle/ioc/MiniIocDemo` | 运行 main |
| 3 | AOP 底层：JDK / CGLIB 动态代理、自调用失效 | `principle/aop/JdkProxyDemo`、`CglibProxyDemo` | 运行 main |
| 4 | Bean 生命周期 | `lifecycle/LifecycleBean`、`LifecycleBeanPostProcessor` | 启动后搜索控制台 `[生命周期]`；点 Exit 关闭看销毁 |
| 5 | 循环依赖与三级缓存 | `lifecycle/CircularA`、`CircularB`、`StartupRunner` | 启动后搜索 `[循环依赖]` |
| 6 | Spring AOP 实战：自定义注解 + 切面 | `aop/LogExecution`、`LogAspect` | 调用登录 / 新增用户，看 `[AOP]` 日志；`GET /demo/beans` 看代理类型 |
| 7 | 自动配置原理 | `com/example/starter/*`、`resources/META-INF/spring/...AutoConfiguration.imports` | `GET /demo/greeting`；改 `demo.greeting.enabled=false` 再试 |
| 8 | Filter / Interceptor / AOP + JWT 鉴权 | `web/*` | `requests.http` 第 1 组，看日志里 `[Filter]` → `[Interceptor]` → `[AOP]` 的顺序 |
| 9 | 全局异常处理、参数校验 | `common/*`、`user/dto/*` | `requests.http` 2.4、2.5、2.9、2.10 |
| 10 | @Transactional 与失效场景 | `tx/*` | 浏览器访问 `/demo/tx/{场景}`，对比 expectedRows 和 actualRows |
| 11 | 综合：用户 CRUD + 分页 + JWT 登录 | `user/*`、`config/MybatisPlusConfig` | `requests.http` 第 2 组 |
| 12 | Spring MVC：请求处理流程、参数绑定、消息转换、自定义参数解析器 | `mvc/*`、`web/WebConfig#addArgumentResolvers` | `requests.http` 第 5 组；`GET /demo/mvc/components` |
| 13 | MyBatis：XML 映射、动态 SQL、#{} 与 ${}、resultMap 一对多、N+1、一级缓存、插件 | `mybatis/*`、`resources/mapper/UserQueryMapper.xml` | `requests.http` 第 6 组，每个响应都带本次执行的 `sqls` |

## 目录结构

```
src/main/java/com/example/
├── demo/
│   ├── DemoApplication.java      启动类（@SpringBootApplication 拆解）
│   ├── principle/                第 1~3 课，纯 Java，不依赖 Spring 容器
│   ├── lifecycle/                Bean 生命周期、循环依赖
│   ├── aop/                      自定义注解 + 切面
│   ├── web/                      Filter、拦截器、JWT、ThreadLocal 用户上下文
│   ├── common/                   统一返回体、业务异常、全局异常处理
│   ├── user/                     用户管理（Controller → Service → Mapper）
│   ├── tx/                       事务失效场景
│   ├── showcase/                 原理观察接口（/demo/beans、/demo/greeting）
│   ├── mvc/                      @CurrentUser 参数解析器、参数绑定演示
│   ├── mybatis/                  手写 XML 的 Mapper、SQL 统计插件、一级缓存演示
│   └── config/                   MyBatis-Plus 分页插件
└── starter/                      模拟第三方 starter，故意放在启动类扫描范围之外

src/main/resources/
├── application.yml
├── schema.sql / data.sql         t_user、t_order、t_tx_log 建表和初始数据
└── mapper/UserQueryMapper.xml    第 13 课的 SQL
```

## 动手实验

看懂代码后，改一改再跑，印象最深：

1. `AnnotationDemo`：把 `RetentionPolicy.RUNTIME` 改成 `CLASS`，注解就读不到了。
2. `CircularA` / `CircularB`：改成构造器注入，启动报 `BeanCurrentlyInCreationException`。
3. `application.yml`：删掉 `allow-circular-references: true`，字段注入的循环依赖也会启动失败。
4. `LogAspect`：把 catch 里的 `throw e` 改成 `return null`（吞异常），再用重复用户名调用新增用户接口：接口返回“成功”但 data 是 null，真正的错误被掩盖了。
5. `GreetingAutoConfiguration`：在 `config` 包里自己 `@Bean` 一个 `GreetingService("Hi")`，看自动配置是否让位。
6. `application.yml` 加 `debug: true`，启动后搜索 `GreetingAutoConfiguration`，看条件评估报告。
7. `WebConfig`：注释掉 `addArgumentResolvers`，再调 `/api/auth/me`：`@CurrentUser Long userId` 没人认领，会被当成普通查询参数 `userId`，取到 null，接口查不到用户。
8. `UserQueryMapper.xml`：把 `<select id="findByNameSafe">` 整段复制一份，启动时报 `Mapped Statements collection already contains value for ...findByNameSafe` —— SQL 的 key 只有“namespace.id”，不含参数类型，这也是 Mapper 方法不能重载的原因。
9. `UserQueryMapper.xml`：把 `<where>` 换成 `WHERE`，访问 `/demo/mybatis/search?minAge=25`，生成 `WHERE AND age >= ?`，SQL 报错。
10. `UserQueryMapper.xml`：删掉 `userWithOrdersJoinMap` 里的 `<id property="id" column="user_id"/>`，访问 `/demo/mybatis/one-to-many/1`：订单仍然合并在一起（没有 `<id>` 时 MyBatis 用其余所有列判断是不是同一行），但返回的 `id` 变成 null —— 带嵌套映射的 resultMap 默认不自动映射没写出来的列。
11. `application.yml`：把 `demo.mybatis.slow-sql-ms` 改成 `0`，所有 SQL 都会以 `[慢 SQL]` 级别打印。

## 换成 MySQL

1. `pom.xml` 把 `h2` 依赖换成 `com.mysql:mysql-connector-j`（scope runtime）。
2. `application.yml` 的 datasource 改成 MySQL 地址，删掉 `h2.console` 配置；首次启动保留 `sql.init.mode: always` 建表，之后改成 `never`。
3. `MybatisPlusConfig` 里 `DbType.H2` 改成 `DbType.MYSQL`。
