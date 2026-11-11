package com.example.demo.mvc;

import com.example.demo.common.Result;
import com.example.demo.mybatis.dto.UserSearchQuery;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring MVC 演示接口，不需要登录。
 * <p>
 * 一个请求进来后 DispatcherServlet 做的事（对照 /demo/mvc/components 的输出看）：
 * <ol>
 *   <li>HandlerMapping：根据 URL 找到处理它的 Controller 方法（HandlerMethod），连同匹配的拦截器组成 HandlerExecutionChain</li>
 *   <li>HandlerAdapter：找到能调用这个 HandlerMethod 的适配器，注解式 Controller 用的是 RequestMappingHandlerAdapter</li>
 *   <li>执行拦截器 preHandle</li>
 *   <li>参数解析：HandlerMethodArgumentResolver 为每个参数取值（下面几个接口演示不同的来源）</li>
 *   <li>反射调用 Controller 方法</li>
 *   <li>返回值处理：方法上有 @ResponseBody（@RestController 自带），就用 HttpMessageConverter 把返回值写成 JSON</li>
 *   <li>执行拦截器 postHandle / afterCompletion；有异常则交给 HandlerExceptionResolver（全局异常处理）</li>
 * </ol>
 * 前后端分离项目返回 JSON，不经过 ViewResolver 视图解析这一步。
 */
@RestController
@RequestMapping("/demo/mvc")
public class MvcDemoController {

    private final ApplicationContext applicationContext;
    private final RequestMappingHandlerAdapter handlerAdapter;

    public MvcDemoController(ApplicationContext applicationContext, RequestMappingHandlerAdapter handlerAdapter) {
        this.applicationContext = applicationContext;
        this.handlerAdapter = handlerAdapter;
    }

    /**
     * 从 URL 路径、查询参数、请求头取值。
     * 例：GET /demo/mvc/params/42?name=张三&amp;tags=java&amp;tags=spring
     */
    @GetMapping("/params/{id}")
    public Result<Map<String, Object>> params(@PathVariable Long id,
                                              @RequestParam String name,
                                              @RequestParam(required = false) List<String> tags,
                                              @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@PathVariable id", id);
        result.put("@RequestParam name", name);
        result.put("@RequestParam tags", tags);
        result.put("@RequestHeader User-Agent", userAgent);
        return Result.ok(result);
    }

    /**
     * 不加任何注解的对象参数：按字段名从查询参数里绑定（相当于省略了 @ModelAttribute），适合多条件查询。
     * 例：GET /demo/mvc/query?username=zhang&amp;minAge=20&amp;orderBy=age
     */
    @GetMapping("/query")
    public Result<UserSearchQuery> query(UserSearchQuery query) {
        return Result.ok(query);
    }

    /**
     * @RequestBody：读取请求体，用 Jackson（MappingJackson2HttpMessageConverter）反序列化。
     * 请求头必须是 Content-Type: application/json，否则报 415。
     */
    @PostMapping("/body")
    public Result<DemoBody> body(@RequestBody DemoBody body) {
        return Result.ok(body);
    }

    public record DemoBody(String name, Integer age, List<String> tags) {
    }

    /**
     * 返回 ResponseEntity 可以同时控制状态码、响应头和响应体；直接返回对象则默认 200。
     */
    @PostMapping("/created")
    public ResponseEntity<Result<String>> created() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Demo", "spring-mvc")
                .body(Result.ok("状态码 201，响应头里有 X-Demo"));
    }

    /**
     * 看看 DispatcherServlet 背后的组件：有哪些 HandlerMapping、哪些消息转换器、我们自定义的参数解析器是否已注册。
     */
    @GetMapping("/components")
    public Result<Map<String, Object>> components() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handlerMappings", applicationContext.getBeansOfType(HandlerMapping.class).values().stream()
                .map(mapping -> mapping.getClass().getSimpleName())
                .toList());
        result.put("messageConverters", handlerAdapter.getMessageConverters().stream()
                .map(converter -> converter.getClass().getSimpleName())
                .toList());
        List<String> resolvers = handlerAdapter.getArgumentResolvers() == null ? List.of()
                : handlerAdapter.getArgumentResolvers().stream()
                .map(resolver -> resolver.getClass().getSimpleName())
                .toList();
        result.put("argumentResolverCount", resolvers.size());
        result.put("customResolverRegistered", resolvers.contains(CurrentUserArgumentResolver.class.getSimpleName()));
        return Result.ok(result);
    }
}
