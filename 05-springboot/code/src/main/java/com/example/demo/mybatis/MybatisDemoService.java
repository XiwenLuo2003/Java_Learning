package com.example.demo.mybatis;

import com.example.demo.user.User;
import com.example.demo.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一级缓存演示。
 * <p>
 * 一级缓存挂在 SqlSession 上（准确说是 SqlSession 里的 Executor 的 localCache）：同一个 SqlSession 内，
 * 同样的 SQL + 同样的参数第二次查询直接返回缓存里的同一个对象，不发 SQL。
 * <p>
 * 和 Spring 整合后，Mapper 实际调用的是 SqlSessionTemplate，它的规则是：
 * <ul>
 *   <li>没有事务：每次调用 Mapper 方法都新开一个 SqlSession，用完就关 —— 一级缓存等于没有</li>
 *   <li>有事务：SqlSession 绑定到当前事务（存在 ThreadLocal 里），事务内多次调用共用一个 —— 一级缓存生效</li>
 * </ul>
 * 同一个 SqlSession 里执行任何 insert / update / delete，或者事务提交、回滚，都会清空一级缓存。
 */
@Service
public class MybatisDemoService {

    private final UserMapper userMapper;

    public MybatisDemoService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 没有事务：两次查询发两条 SQL，拿到两个不同的对象 */
    public Map<String, Object> queryTwiceWithoutTransaction() {
        User first = userMapper.selectById(1L);
        User second = userMapper.selectById(1L);
        return compare(first, second);
    }

    /** 事务内：第二次命中一级缓存，只发一条 SQL，拿到的是同一个对象 */
    @Transactional(readOnly = true)
    public Map<String, Object> queryTwiceInTransaction() {
        User first = userMapper.selectById(1L);
        User second = userMapper.selectById(1L);
        return compare(first, second);
    }

    /** 事务内，两次查询中间有一次 update：update 清空了一级缓存，第二次查询重新发 SQL */
    @Transactional
    public Map<String, Object> queryUpdateQueryInTransaction() {
        User first = userMapper.selectById(1L);
        userMapper.updateById(first);
        User second = userMapper.selectById(1L);
        return compare(first, second);
    }

    private Map<String, Object> compare(User first, User second) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sameObject", first == second);
        result.put("firstHash", System.identityHashCode(first));
        result.put("secondHash", System.identityHashCode(second));
        return result;
    }
}
