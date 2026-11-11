package com.example.demo.mybatis;

import com.example.demo.common.BizException;
import com.example.demo.common.Result;
import com.example.demo.mybatis.dto.UserSearchQuery;
import com.example.demo.user.User;
import com.example.demo.user.dto.UserVO;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * MyBatis 演示接口，不需要登录。每个接口都会返回本次请求实际执行的 SQL（由 SqlStatsInterceptor 记录），
 * 直接对照 resources/mapper/UserQueryMapper.xml 看动态 SQL 生成了什么。
 */
@RestController
@RequestMapping("/demo/mybatis")
public class MybatisDemoController {

    private final UserQueryMapper userQueryMapper;
    private final MybatisDemoService mybatisDemoService;

    public MybatisDemoController(UserQueryMapper userQueryMapper, MybatisDemoService mybatisDemoService) {
        this.userQueryMapper = userQueryMapper;
        this.mybatisDemoService = mybatisDemoService;
    }

    /**
     * 动态 SQL：参数传得不同，生成的 WHERE 不同。
     * 例：/demo/mybatis/search、/demo/mybatis/search?minAge=25&amp;orderBy=age、/demo/mybatis/search?username=zhang
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(UserSearchQuery query) {
        return Result.ok(recordSql(() -> toVO(userQueryMapper.search(query))));
    }

    /** foreach：/demo/mybatis/by-ids?ids=1,3,5 */
    @GetMapping("/by-ids")
    public Result<Map<String, Object>> byIds(@RequestParam List<Long> ids) {
        if (ids.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "ids 不能为空");
        }
        return Result.ok(recordSql(() -> toVO(userQueryMapper.selectByIds(ids))));
    }

    /**
     * #{} 和 ${} 对比。
     * 正常：/demo/mybatis/injection?name=admin，两种写法结果一样
     * 注入：/demo/mybatis/injection?name=' OR '1'='1 （浏览器会自动编码），${} 查出全表，#{} 查不到
     */
    @GetMapping("/injection")
    public Result<Map<String, Object>> injection(@RequestParam String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("safe #{}", recordSql(() -> toVO(userQueryMapper.findByNameSafe(name))));
        try {
            result.put("unsafe ${}", recordSql(() -> toVO(userQueryMapper.findByNameUnsafe(name))));
        } catch (DataAccessException e) {
            result.put("unsafe ${}", "SQL 被拼坏了，执行报错：" + e.getMostSpecificCause().getMessage());
        }
        return Result.ok(result);
    }

    /** 一对多（JOIN + collection）：/demo/mybatis/one-to-many/1 */
    @GetMapping("/one-to-many/{id}")
    public Result<Map<String, Object>> oneToMany(@PathVariable Long id) {
        return Result.ok(recordSql(() -> userQueryMapper.selectWithOrders(id)));
    }

    /** N+1：嵌套查询发 1 + 5 条 SQL，JOIN 只发 1 条，结果一样 */
    @GetMapping("/n-plus-one")
    public Result<Map<String, Object>> nPlusOne() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nestedSelect", recordSql(userQueryMapper::selectAllWithOrdersNPlusOne));
        result.put("join", recordSql(userQueryMapper::selectAllWithOrdersJoin));
        return Result.ok(result);
    }

    /** 一级缓存：对比有无事务时，两次相同查询发了几条 SQL、拿到的是不是同一个对象 */
    @GetMapping("/cache")
    public Result<Map<String, Object>> cache() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("withoutTransaction", recordSql(mybatisDemoService::queryTwiceWithoutTransaction));
        result.put("inTransaction", recordSql(mybatisDemoService::queryTwiceInTransaction));
        result.put("inTransactionWithUpdate", recordSql(mybatisDemoService::queryUpdateQueryInTransaction));
        return Result.ok(result);
    }

    private Map<String, Object> recordSql(Supplier<Object> action) {
        SqlStatsInterceptor.startRecording();
        Object data;
        List<String> sqls;
        try {
            data = action.get();
        } finally {
            sqls = SqlStatsInterceptor.stopRecording();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sqlCount", sqls.size());
        result.put("sqls", sqls);
        result.put("data", data);
        return result;
    }

    private List<UserVO> toVO(List<User> users) {
        return users.stream().map(UserVO::from).toList();
    }
}
