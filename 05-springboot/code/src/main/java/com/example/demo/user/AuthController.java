package com.example.demo.user;

import com.example.demo.common.Result;
import com.example.demo.user.dto.LoginRequest;
import com.example.demo.user.dto.UserVO;
import com.example.demo.web.UserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 登录成功返回 token；这个路径在 WebConfig 里被放行，不需要 token */
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    /** 当前登录用户：userId 是拦截器解析 token 后放进 ThreadLocal 的，这里不需要任何参数 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getById(UserContext.getUserId()));
    }
}
