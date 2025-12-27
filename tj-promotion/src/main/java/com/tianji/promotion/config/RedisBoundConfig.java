package com.tianji.promotion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisBoundConfig {

    private static final String SERIAL_KEY = "code:serial";

    @Bean
    public BoundValueOperations<String, String> serialOps(StringRedisTemplate redisTemplate) {
        return redisTemplate.boundValueOps(SERIAL_KEY);
    }

}
