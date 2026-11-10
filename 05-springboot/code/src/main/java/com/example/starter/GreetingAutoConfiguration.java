package com.example.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 自己写的自动配置类，完整模拟 spring-boot-starter-xxx 的工作方式：
 * <ol>
 *   <li>@EnableAutoConfiguration 通过 AutoConfigurationImportSelector 读取所有 jar 包里的
 *       META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *       （本项目里这个文件就在 src/main/resources 下，只写了一行本类的全类名）</li>
 *   <li>逐个检查条件注解，全部满足才生效</li>
 *   <li>生效后，里面的 @Bean 被注册进容器</li>
 * </ol>
 * 这个包（com.example.starter）不在启动类的扫描范围内，所以它只能靠上面这套机制被加载 ——
 * 这正是第三方 starter 的处境：Spring 不可能扫描到别人 jar 包里的类。
 * <p>
 * 动手试试：
 * <ul>
 *   <li>yml 里设置 demo.greeting.enabled=false，GET /demo/greeting 会提示 GreetingService 不存在（@ConditionalOnProperty 不满足）</li>
 *   <li>在任意配置类里自己 @Bean 一个 GreetingService，自动配置的就会让位（@ConditionalOnMissingBean），这就是“约定优于配置，配置可以覆盖约定”</li>
 *   <li>yml 里加 debug: true 启动，控制台会打印 CONDITIONS EVALUATION REPORT，能看到每个自动配置类生效 / 不生效的原因</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(GreetingService.class)
@ConditionalOnProperty(prefix = "demo.greeting", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GreetingProperties.class)
public class GreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GreetingService greetingService(GreetingProperties properties) {
        return new GreetingService(properties.getPrefix());
    }
}
