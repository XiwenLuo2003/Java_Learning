package com.example.redis.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 对应黑马点评的 config/RedissonConfig：地址不写死，复用 application.yml 里 spring.data.redis 的配置。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties props) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + props.getHost() + ":" + props.getPort())
                .setDatabase(props.getDatabase());
        if (StringUtils.hasText(props.getPassword())) {
            server.setPassword(props.getPassword());
        }
        return Redisson.create(config);
    }
}
