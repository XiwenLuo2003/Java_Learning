package com.example.demo.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Bean 生命周期演示。启动项目，在控制台搜索 “[生命周期]”，预期顺序：
 * <pre>
 * [生命周期] 1. 构造方法 —— 实例化（createBeanInstance）
 * [生命周期] 2. @Autowired setter —— 属性填充（populateBean）
 * [生命周期] 3. BeanNameAware#setBeanName -> lifecycleBean
 * [生命周期] 4. ApplicationContextAware#setApplicationContext
 * [生命周期] 5. BeanPostProcessor#postProcessBeforeInitialization
 * [生命周期] 6. @PostConstruct
 * [生命周期] 7. InitializingBean#afterPropertiesSet
 * [生命周期] 8. BeanPostProcessor#postProcessAfterInitialization（AOP 代理就是在这一步生成的）
 * ……项目运行中……
 * [生命周期] 9. @PreDestroy
 * [生命周期] 10. DisposableBean#destroy
 * </pre>
 * 3~8 都属于“初始化”（initializeBean）。9、10 只有正常关闭容器才会打印：在 IDEA 里点 Exit 按钮，而不是强制停止。
 */
@Component
public class LifecycleBean implements BeanNameAware, ApplicationContextAware, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LifecycleBean.class);

    public LifecycleBean() {
        log.info("[生命周期] 1. 构造方法 —— 实例化（createBeanInstance）");
    }

    @Autowired
    public void setEnvironment(Environment environment) {
        log.info("[生命周期] 2. @Autowired setter —— 属性填充（populateBean）");
    }

    @Override
    public void setBeanName(String name) {
        log.info("[生命周期] 3. BeanNameAware#setBeanName -> {}", name);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        log.info("[生命周期] 4. ApplicationContextAware#setApplicationContext");
    }

    @PostConstruct
    public void postConstruct() {
        log.info("[生命周期] 6. @PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        log.info("[生命周期] 7. InitializingBean#afterPropertiesSet");
    }

    @PreDestroy
    public void preDestroy() {
        log.info("[生命周期] 9. @PreDestroy");
    }

    @Override
    public void destroy() {
        log.info("[生命周期] 10. DisposableBean#destroy");
    }
}
