package com.algomeet.xmpp.chatservice.config;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.cluster.listener.ClusterMessageListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * <p>Configuration class for Redis-based cluster synchronization and message broadcasting.</p>
 * 
 * <p>In a distributed Algomeet environment, this class enables the "Cluster Fabric" by:</p>
 * <ul>
 *     <li><b>Pub/Sub Setup:</b> Defining a global topic where nodes broadcast XMPP stanzas.</li>
 *     <li><b>Async Listening:</b> Configuring a container to listen for incoming cluster 
 *         sync events without blocking the main Netty threads.</li>
 *     <li><b>Serialization:</b> Ensuring that {@link ClusterSyncMessage} objects are 
 *         correctly converted to JSON for cross-node compatibility.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Configuration
public class RedisConfig {

    @Value("${cluster.sync.topic:cluster.sync.topic}")
    private String syncTopic;
    
    /**
     * Defines the Redis channel/topic name used for horizontal scaling.
     * All server nodes subscribe to this same topic to receive routed stanzas.
     */
    @Bean
    public ChannelTopic topic() {
        return new ChannelTopic(syncTopic);
    }
    
    /**
     * Adapts the {@link ClusterMessageListener} to the Redis message listener interface.
     * It specifically points Redis to the {@code onMessage} method of our listener.
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(ClusterMessageListener clusterMessageListener) {
        return new MessageListenerAdapter(clusterMessageListener, "onMessage");
    }
    
    /**
     * The heart of the Redis subscriber logic. This container manages the lifecycle 
     * of the connection to Redis and dispatches incoming messages to our adapter.
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, topic());
        return container;
    }   
        
    /**
     * Configures the {@link RedisTemplate} for publishing {@link ClusterSyncMessage} DTOs.
     * 
     * <p><b>Key Configurations:</b></p>
     * <ul>
     *     <li><b>JavaTimeModule:</b> Supports modern Java Date/Time types (Instant, LocalDateTime).</li>
     *     <li><b>Disable Timestamps:</b> Writes dates in ISO-8601 format for better readability in logs.</li>
     *     <li><b>Typing:</b> Deactivates default typing to prevent {@code @class} metadata from 
     *         bloating the Redis payload, keeping the JSON "clean."</li>
     * </ul>
     */
    @Bean
    public RedisTemplate<String, ClusterSyncMessage> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ClusterSyncMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Prevent writing @class into JSON to keep it compatible with other potential consumers
        mapper.deactivateDefaultTyping();
        
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        // Use Strings for keys and JSON for values
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}