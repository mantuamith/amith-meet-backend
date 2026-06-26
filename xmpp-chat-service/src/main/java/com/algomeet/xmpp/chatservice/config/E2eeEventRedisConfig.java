package com.algomeet.xmpp.chatservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.algomeet.common.properties.CommonRedisTopicProperties;
import com.algomeet.xmpp.chatservice.dto.E2eeEvent;
import com.algomeet.xmpp.chatservice.listener.E2eeEventMessageListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;
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
 * {@link E2eeEvent} DTOs to the cluster fabric.</li>
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

    /** Centralized properties holding the Redis topic name for E2EE events. */
    private final CommonRedisTopicProperties redisTopicProperties;
	
    /** Factory used to create connections to the Redis server. */
    private final RedisConnectionFactory connectionFactory;
    
    /** The business logic listener that processes incoming Signal/E2EE synchronization messages. */
    private final E2eeEventMessageListener e2eeEventMessageListener;

    /**
     * <p>Initializes the localized Redis Message Listener for E2EE events.</p>
     *
     * <p>This method executes post-construction to configure the subscriber infrastructure. 
     * By instantiating the listener adapter and container locally, we avoid cluttering 
     * the Spring context with infrastructure-level beans that are not needed by 
     * other application components.</p>
     *
     * <p>The container is explicitly started here as it is not managed by the 
     * standard Spring bean lifecycle handlers.</p>
     */
    @PostConstruct
    public void setupClusterSubscription() {
        String topic = redisTopicProperties.getE2eeEvents();
        log.info("Initializing localized Redis E2EE Event subscriber on topic: {}", topic);
                
        // 1. Create the adapter
        MessageListenerAdapter adapter = new MessageListenerAdapter(e2eeEventMessageListener, "onMessage");
        
        // 2. CRITICAL: Initialize the adapter's internal invoker manually
        adapter.afterPropertiesSet(); 
        
        // 3. Create the container
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(adapter, new ChannelTopic(topic));
        
        // 4. Initialize and start the container
        container.afterPropertiesSet();
        container.start();
    }

    /**
     * <p>Configures the {@link RedisTemplate} used for broadcasting {@link E2eeEvent} objects.</p>
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