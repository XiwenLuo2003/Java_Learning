package com.example.demo.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

/**
 * BeanPostProcessor（后置处理器）会作用于容器里的<b>每一个</b> Bean，是 Spring 最重要的扩展点：
 * @Autowired 注入、@PostConstruct、AOP 代理，背后都是不同的 BeanPostProcessor。
 * <p>
 * 这里只对 LifecycleBean 打日志，避免刷屏。
 * 实现 PriorityOrdered 并设为最高优先级，是为了排在处理 @PostConstruct 的 CommonAnnotationBeanPostProcessor 之前，
 * 否则第 5、6 步的打印顺序会反过来。
 */
@Component
public class LifecycleBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(LifecycleBeanPostProcessor.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof LifecycleBean) {
            log.info("[生命周期] 5. BeanPostProcessor#postProcessBeforeInitialization");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof LifecycleBean) {
            log.info("[生命周期] 8. BeanPostProcessor#postProcessAfterInitialization（AOP 代理就是在这一步生成的）");
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
