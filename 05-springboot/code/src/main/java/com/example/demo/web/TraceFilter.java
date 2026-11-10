package com.example.demo.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter 是 Servlet 规范的组件，由 Tomcat 调用，位于 DispatcherServlet 之前，所以：
 * <ul>
 *   <li>所有请求都会经过它（包括静态资源、/h2-console），适合做 traceId、编码、跨域、XSS 过滤这类“与业务无关”的事</li>
 *   <li>它拿不到“要调用哪个 Controller 方法”这种 Spring MVC 信息</li>
 *   <li>它里面抛出的异常，@RestControllerAdvice 捕获不到（那是 DispatcherServlet 内部的机制）</li>
 * </ul>
 * 这里给每个请求生成 traceId 放进 MDC，日志格式里的 %X{traceId} 会自动打印它，同一个请求的日志就能串起来。
 * 注册方式见 WebConfig#traceFilter。
 */
public class TraceFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);
        ((HttpServletResponse) response).setHeader("X-Trace-Id", traceId);

        long start = System.currentTimeMillis();
        log.info("[Filter] 进入 {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("[Filter] 离开 {} 耗时={}ms", httpRequest.getRequestURI(), System.currentTimeMillis() - start);
            MDC.remove("traceId");
        }
    }
}
