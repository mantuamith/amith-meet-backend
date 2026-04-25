package com.algomeet.xmpp.chatservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MissedCallStreamRedisConfig {
	 /** Factory used to create connections to the Redis server. */
    private final RedisConnectionFactory connectionFactory;

    @Bean    
    public RedisTemplate<String, String> missedCallStringRedisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        /**
         * Use plain string serialization for both channels and payloads.
         *
         * Faster than JSON serializers for Pub/Sub transport messaging.
         */
        StringRedisSerializer serializer = new StringRedisSerializer();

        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }
}
