package com.tonyyuan.paymentappbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration class.
 *
 * Note:
 * - In a production environment, this connects to a Redis server
 *   (defaults to localhost:6379 when using LettuceConnectionFactory).
 * - For this project/demo, Redis is not required. We use a simple
 *   ConcurrentHashMap-based cache implementation instead.
 * - This config is included for completeness and can be enabled if
 *   you run a Redis server locally.
 */
@Configuration
public class RedisConfig {

    /**
     * Configure a Redis connection factory using Lettuce.
     * Defaults to localhost:6379 if no extra configuration is provided.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    /**
     * StringRedisTemplate bean for performing Redis operations
     * with string keys and values.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
