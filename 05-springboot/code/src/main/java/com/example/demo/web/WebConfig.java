package com.example.demo.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 Filter 和拦截器。
 * <p>
 * 一个请求的完整路径（括号里是对应的类）：
 * <pre>
 * Tomcat -> Filter（TraceFilter）-> DispatcherServlet -> Interceptor#preHandle（LoginInterceptor）
 *        -> AOP 代理（LogAspect、事务）-> Controller / Service 真正的方法
 *        -> Interceptor#afterCompletion -> Filter 收尾
 * </pre>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor) {
        this.loginInterceptor = loginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }

    @Bean
    public FilterRegistrationBean<TraceFilter> traceFilter() {
        FilterRegistrationBean<TraceFilter> registration = new FilterRegistrationBean<>(new TraceFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
