package com.algomeet.signalservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.algomeet.common.dto.ConversationSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    public RedisTemplate<String, String> streamStringRedisTemplate(RedisConnectionFactory connectionFactory) {
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
	
	@Bean
    public RedisTemplate<String, ConversationSettings> conversationSettingsRedisTemplate(
            RedisConnectionFactory factory, 
            ObjectMapper objectMapper) { // Injects Spring's managed ObjectMapper
        
        RedisTemplate<String, ConversationSettings> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        
        // Pass the ObjectMapper so Jackson knows how to correctly unpack classes 
        // without defaulting to LinkedHashMap
        Jackson2JsonRedisSerializer<ConversationSettings> valueSerializer = 
                new Jackson2JsonRedisSerializer<>(objectMapper, ConversationSettings.class);

        // Configure the template key/value pairs
        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        
        // Configure hash fields as well just in case they are used in the future
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}