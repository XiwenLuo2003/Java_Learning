package com.example.demo.mvc;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 贴在 Controller 方法的参数上，表示“把当前登录用户的 id 注入进来”：
 * <pre>
 * public Result&lt;UserVO&gt; me(@CurrentUser Long userId)
 * </pre>
 * 和 @RequestParam、@PathVariable 一样，它本身什么都不做，由 {@link CurrentUserArgumentResolver} 负责解析。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CurrentUser {
}
