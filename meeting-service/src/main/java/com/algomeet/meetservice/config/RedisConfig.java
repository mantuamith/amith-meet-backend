package com.algomeet.meetservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.ZoneId;

@Configuration
@ConditionalOnProperty(name = "algomeet.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Boolean> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Boolean> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Keys are Strings
        template.setKeySerializer(new StringRedisSerializer());

        // Values are Booleans, serialize as "true"/"false"
        template.setValueSerializer(new GenericToStringSerializer<>(Boolean.class));

        // Optional: also apply for hash keys/values
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericToStringSerializer<>(Boolean.class));

        template.afterPropertiesSet();
        return template;
    }

    /** StringRedisTemplate for atomic counters (INCR). */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Common timezone for ID generation to avoid cross-node day-rollover issues.
     * Defaults to UTC; can be overridden via 'algomeet.idgen.timezone'.
     */
    @Bean(name = "idGenZoneId")
    public ZoneId idGenZoneId(@Value("${algomeet.idgen.timezone:UTC}") String tz) {
        return ZoneId.of(tz);
    }
}
