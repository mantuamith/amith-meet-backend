package com.algomeet.xmpp.chatservice.config;

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
import com.algomeet.xmpp.chatservice.properties.RedisTopicProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;
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
public class ClusterSyncRedisConfig {
	private final RedisTopicProperties redisTopicProperties;

    /** Factory used to create connections to the Redis server. */
    private final RedisConnectionFactory connectionFactory;
    
    /** The business logic listener that processes incoming cluster synchronization messages. */
    private final ClusterMessageListener clusterMessageListener;


    /**
     * <p>Initializes the localized Redis Message Listener subscription.</p>
     * * <p>This method is executed post-construction to set up the Pub/Sub infrastructure 
     * without exposing the {@link RedisMessageListenerContainer} or {@link MessageListenerAdapter} 
     * as global beans in the Spring context. This localization prevents potential auto-wiring 
     * conflicts and reduces context overhead.</p>
     * * <p>The container is manually triggered via {@code afterPropertiesSet()} and {@code start()} 
     * as it is not managed by the standard Spring Bean lifecycle.</p>
     */
    @PostConstruct
    public void setupClusterSubscription() {
    	String topic = redisTopicProperties.getClusterSyncTopic();
        log.info("Initializing localized Redis Cluster Subscriber on topic: {}", topic);
        
        // 1. Create the adapter
        MessageListenerAdapter adapter = new MessageListenerAdapter(clusterMessageListener, "onMessage");
        
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
     * <p>Configures the shared {@link RedisTemplate} for publishing {@link ClusterSyncMessage} DTOs.</p>
     * * <p><b>Serialization Strategy:</b></p>
     * <ul>
     * <li><b>Keys:</b> Uses {@link StringRedisSerializer} for human-readable channel keys.</li>
     * <li><b>Values:</b> Employs {@link GenericJackson2JsonRedisSerializer} for JSON-based 
     * payloads, ensuring cross-platform compatibility.</li>
     * <li><b>Time Support:</b> Includes {@code JavaTimeModule} to handle modern Java 8+ Date/Time types.</li>
     * <li><b>Clean JSON:</b> Deactivates default typing to omit {@code @class} metadata, 
     * keeping the payload size small and readable.</li>
     * </ul>
     * * @return A configured {@link RedisTemplate} instance for cluster-wide message broadcasting.
     */
    @Bean
    public RedisTemplate<String, ClusterSyncMessage> clusterRedisTemplate() {
        RedisTemplate<String, ClusterSyncMessage> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Keep JSON "clean" by not including class type metadata
        mapper.deactivateDefaultTyping();
        
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        return template;
    }
}