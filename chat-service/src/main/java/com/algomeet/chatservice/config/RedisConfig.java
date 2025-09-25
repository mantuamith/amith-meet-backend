package com.algomeet.chatservice.config;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.algomeet.chatservice.dto.SessionMetadata;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Set<SessionMetadata>> userSessionsRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Set<SessionMetadata>> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());

        return template;
    }
}
