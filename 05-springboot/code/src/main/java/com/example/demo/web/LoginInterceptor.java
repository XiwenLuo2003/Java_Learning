package com.example.demo.web;

import com.example.demo.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一登录鉴权拦截器（小红书一面原题）。
 * <p>
 * HandlerInterceptor 是 Spring MVC 的组件，由 DispatcherServlet 在找到 Controller 方法之后、调用之前执行：
 * <ul>
 *   <li>preHandle：Controller 执行前。返回 false 或抛异常就不会进 Controller</li>
 *   <li>postHandle：Controller 正常返回后、视图渲染前（Controller 抛异常则不执行）</li>
 *   <li>afterCompletion：请求完全结束后，无论成功失败都会执行，适合做清理</li>
 * </ul>
 * 和 Filter 相比，它能拿到 HandlerMethod（可以读取 Controller 方法上的注解做细粒度权限），
 * 抛出的异常也能被全局异常处理器捕获。
 * <p>
 * 拦截哪些路径在 WebConfig 里配置：拦截 /api/**，放行 /api/auth/login。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public LoginInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        log.info("[Interceptor] preHandle -> {}#{}",
                handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName());

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未登录，请在请求头携带 Authorization: Bearer <token>");
        }
        Long userId = jwtUtil.parseUserId(header.substring(BEARER_PREFIX.length()));
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
        log.info("[Interceptor] afterCompletion，已清理 UserContext");
    }
}
