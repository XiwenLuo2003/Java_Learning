package com.example.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 把 application.yml 里 demo.greeting.* 的配置绑定到这个对象上。
 * 对比 web/JwtUtil 里的 @Value：配置项多、需要分组时用 @ConfigurationProperties 更清晰，还支持类型校验和 IDE 提示。
 */
@ConfigurationProperties(prefix = "demo.greeting")
public class GreetingProperties {

    /** 是否启用 GreetingService */
    private boolean enabled = true;

    /** 问候语前缀 */
    private String prefix = "Hello";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
