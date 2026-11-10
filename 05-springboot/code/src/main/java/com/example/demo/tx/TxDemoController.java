package com.example.demo.tx;

import com.example.demo.common.BizException;
import com.example.demo.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事务失效场景演示入口，不需要登录，浏览器直接访问：
 * <ul>
 *   <li>GET /demo/tx           列出所有场景</li>
 *   <li>GET /demo/tx/{scene}   运行某个场景，返回抛出的异常和表里剩余行数</li>
 * </ul>
 */
@RestController
@RequestMapping("/demo/tx")
public class TxDemoController {

    @FunctionalInterface
    interface TxAction {
        void run() throws Exception;
    }

    record Scene(String description, long expectedRows, TxAction action) {
    }

    public record SceneInfo(String scene, String description, long expectedRows) {
    }

    public record TxResult(String scene, String description, String exception, long expectedRows, long actualRows,
                           String conclusion) {
    }

    private final TxDemoService txDemoService;
    private final Map<String, Scene> scenes = new LinkedHashMap<>();

    public TxDemoController(TxDemoService service) {
        this.txDemoService = service;
        scenes.put("normal", new Scene("正常回滚（对照组）", 0, service::normalRollback));
        scenes.put("self-invocation", new Scene("自调用：this 调用事务方法，绕过代理", 1, service::selfInvocation));
        scenes.put("self-invocation-fixed", new Scene("自调用修复：通过代理对象调用", 0, service::selfInvocationFixed));
        scenes.put("checked-exception", new Scene("抛受检异常，默认不回滚", 1, service::checkedException));
        scenes.put("checked-exception-fixed", new Scene("受检异常修复：rollbackFor = Exception.class", 0, service::checkedExceptionFixed));
        scenes.put("swallow", new Scene("异常被 catch 吞掉，代理感知不到", 1, service::swallowException));
        scenes.put("multi-thread", new Scene("子线程不在事务里，子线程的写入已提交", 1, service::multiThread));
        scenes.put("requires-new", new Scene("内层 REQUIRES_NEW 独立提交，不随外层回滚", 1, service::requiresNew));
        scenes.put("rollback-only", new Scene("内层 REQUIRED 失败被外层 catch，整体回滚并抛 UnexpectedRollbackException", 0, service::rollbackOnly));
    }

    @GetMapping
    public Result<List<SceneInfo>> list() {
        return Result.ok(scenes.entrySet().stream()
                .map(e -> new SceneInfo(e.getKey(), e.getValue().description(), e.getValue().expectedRows()))
                .toList());
    }

    @GetMapping("/{scene}")
    public Result<TxResult> run(@PathVariable String scene) {
        Scene target = scenes.get(scene);
        if (target == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "未知场景 " + scene + "，可选: " + scenes.keySet());
        }

        txDemoService.clear();
        String exception = "无";
        try {
            target.action().run();
        } catch (Exception e) {
            exception = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        long rows = txDemoService.count();
        String conclusion = rows == 0 ? "数据全部回滚" : "表里残留 " + rows + " 行，事务没有按预期全部回滚";
        return Result.ok(new TxResult(scene, target.description(), exception, target.expectedRows(), rows, conclusion));
    }
}
