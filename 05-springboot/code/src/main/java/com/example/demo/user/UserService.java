package com.example.demo.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.aop.LogExecution;
import com.example.demo.common.BizException;
import com.example.demo.user.dto.LoginRequest;
import com.example.demo.user.dto.PageResult;
import com.example.demo.user.dto.UserCreateRequest;
import com.example.demo.user.dto.UserUpdateRequest;
import com.example.demo.user.dto.UserVO;
import com.example.demo.web.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户业务。这里用的是构造器注入（Spring 官方推荐）：
 * 依赖可以声明成 final，对象一创建就是完整可用的；依赖过多时构造器参数会很长，反过来提醒你这个类职责太多了；
 * 写单元测试时直接 new UserService(mockMapper, mockJwt) 就行，不需要启动 Spring。
 * <p>
 * 方法上的 @LogExecution 和 @Transactional 都是靠 AOP 代理生效的，所以容器里的 UserService 其实是 CGLIB 子类。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public UserService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    @LogExecution("用户登录")
    public String login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        // 用户不存在和密码错误返回同一个提示，避免被人用来探测哪些用户名已注册
        if (user == null || !user.getPassword().equals(request.password())) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return jwtUtil.createToken(user.getId(), user.getUsername());
    }

    public UserVO getById(Long id) {
        return UserVO.from(findOrThrow(id));
    }

    public PageResult<UserVO> page(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                .orderByAsc(User::getId);
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(UserVO::from).toList());
    }

    @LogExecution("新增用户")
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateRequest request) {
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username()));
        if (exists) {
            throw new BizException(HttpStatus.CONFLICT, "用户名已存在: " + request.username());
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(request.password());
        user.setNickname(request.nickname());
        user.setAge(request.age());
        userMapper.insert(user);
        return user.getId();
    }

    @LogExecution("修改用户")
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpdateRequest request) {
        findOrThrow(id);
        User user = new User();
        user.setId(id);
        user.setNickname(request.nickname());
        user.setAge(request.age());
        userMapper.updateById(user);
    }

    @LogExecution("删除用户")
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (userMapper.deleteById(id) == 0) {
            throw new BizException(HttpStatus.NOT_FOUND, "用户不存在: " + id);
        }
    }

    private User findOrThrow(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "用户不存在: " + id);
        }
        return user;
    }
}
