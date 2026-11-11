package com.example.demo.mvc;

import com.example.demo.common.BizException;
import com.example.demo.web.UserContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 自定义参数解析器。
 * <p>
 * Spring MVC 调用 Controller 方法前，要为每个参数找到值。它内置了几十个 HandlerMethodArgumentResolver，
 * 挨个问“这个参数你支持吗”（supportsParameter），第一个说支持的负责给出值（resolveArgument）：
 * <ul>
 *   <li>@RequestParam → RequestParamMethodArgumentResolver，从 URL 查询参数里取</li>
 *   <li>@PathVariable → PathVariableMethodArgumentResolver，从路径里取</li>
 *   <li>@RequestBody → RequestResponseBodyMethodProcessor，用 HttpMessageConverter（Jackson）把 JSON 转成对象</li>
 * </ul>
 * 我们按同样的套路加一个：参数上有 @CurrentUser 就从 UserContext（拦截器放进 ThreadLocal 的值）取。
 * 注册在 WebConfig#addArgumentResolvers。
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
