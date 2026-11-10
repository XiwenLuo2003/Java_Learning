package com.example.demo.user;

import com.example.demo.common.Result;
import com.example.demo.user.dto.PageResult;
import com.example.demo.user.dto.UserCreateRequest;
import com.example.demo.user.dto.UserUpdateRequest;
import com.example.demo.user.dto.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RESTful 风格的用户管理接口，全部需要登录（/api/** 被 LoginInterceptor 拦截）。
 * Controller 只做参数接收和校验，业务逻辑放在 Service —— 这样事务、切面都加在 Service 层，Controller 保持很薄。
 * <p>
 * pageSize 上的 @Min / @Max 由 Spring MVC 6.1+ 内置的方法参数校验处理，失败抛 HandlerMethodValidationException。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") @Min(1) long pageNum,
                                           @RequestParam(defaultValue = "10") @Min(1) @Max(100) long pageSize,
                                           @RequestParam(required = false) String keyword) {
        return Result.ok(userService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public Result<UserVO> get(@PathVariable Long id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.update(id, request);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }
}
