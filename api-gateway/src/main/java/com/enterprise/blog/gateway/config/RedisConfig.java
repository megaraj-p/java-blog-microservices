package com.enterprise.blog.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis configuration for the reactive API Gateway.
 * Used by Spring Cloud Gateway's Redis-backed RequestRateLimiter.
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:redis_secret}")
    private String redisPassword;

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            org.springframework.data.redis.connection.ReactiveRedisConnectionFactory factory) {
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }
}
