package com.algomeet.xmpp.chatservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Combined Redis Configuration for Algomeet Cluster Synchronization.</p>
 * * <p>This class serves as the backbone for horizontal scaling in the Algomeet environment. 
 * It manages two primary responsibilities:</p>
 * <ul>
 * <li><b>Outbound:</b> Providing a shared {@link RedisTemplate} for publishing 
 * XMPP stanzas to other nodes in the cluster.</li>
 * <li><b>Inbound:</b> Setting up a localized, self-starting subscriber container 
 * that listens for synchronization events from the Redis Pub/Sub fabric.</li>
 * </ul>
 * * @author Algomeet Core Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {
    private final RedisConnectionFactory connectionFactory;
	
	@Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use JSON for values - much faster and more compact than JDK default
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
	
	@Bean    
    public RedisTemplate<String, String> streamStringRedisTemplate() {
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