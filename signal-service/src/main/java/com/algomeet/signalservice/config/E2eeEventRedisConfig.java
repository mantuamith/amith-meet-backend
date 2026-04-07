package com.algomeet.signalservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.algomeet.signalservice.dto.E2eeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Redis Configuration for Algomeet E2EE (End-to-End Encryption) Event Synchronization.</p>
 *
 * <p>This class manages the distribution and subscription of security-sensitive events 
 * (such as Signal Protocol PreKey updates, device list changes, and session resets) 
 * across the server cluster. It ensures that encryption state remains consistent 
 * regardless of which node a client is currently connected to.</p>
 *
 * <ul>
 * <li><b>Outbound:</b> Provides a {@link RedisTemplate} specifically for publishing 
 * {@link SignalEvent} DTOs to the cluster fabric.</li>
 * <li><b>Inbound:</b> Sets up a localized, non-bean {@link RedisMessageListenerContainer} 
 * to handle incoming E2EE state changes asynchronously.</li>
 * </ul>
 *
 * @author Algomeet Core Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class E2eeEventRedisConfig {
    /** Factory used to create connections to the Redis server. */
    private final RedisConnectionFactory connectionFactory;

    /**
     * <p>Configures the {@link RedisTemplate} used for broadcasting {@link SignalEvent} objects.</p>
     *
     * <p><b>Data Handling Strategy:</b></p>
     * <ul>
     * <li><b>Serialization:</b> Uses JSON serialization to allow potential cross-service 
     * compatibility (e.g., if a non-Java service needs to monitor security events).</li>
     * <li><b>Time Management:</b> Leverages {@link JavaTimeModule} to ensure that event 
     * timestamps (Instant/OffsetDateTime) are handled natively and accurately.</li>
     * <li><b>Polymorphism:</b> Default typing is deactivated to ensure the JSON remains 
     * pure and free of Java-specific class metadata, which reduces payload overhead.</li>
     * </ul>
     *
     * @return A configured {@link RedisTemplate} for secure event distribution.
     */
    @Bean
    public RedisTemplate<String, E2eeEvent> e2eeRedisTemplate() {
        RedisTemplate<String, E2eeEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Use ISO-8601 for dates instead of numeric timestamps for better debugging
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Ensure stanzas and events are clean JSON without @class attributes
        mapper.deactivateDefaultTyping();
        
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        // Standard String keys, JSON values
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}