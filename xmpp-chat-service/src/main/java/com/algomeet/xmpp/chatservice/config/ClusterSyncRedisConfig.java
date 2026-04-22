package com.algomeet.xmpp.chatservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.algomeet.xmpp.chatservice.cluster.listener.ClusterMessageListener;
import com.algomeet.xmpp.chatservice.properties.RedisTopicProperties;

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
     * <p>Configures the dedicated {@link RedisTemplate} used for high-speed cluster
     * Pub/Sub messaging between application nodes.</p>
     *
     * <p>This template is optimized for lightweight transport strings rather than
     * JSON DTO serialization. Cluster messages are manually encoded into compact
     * delimited strings before publishing, reducing CPU overhead and garbage
     * collection pressure under heavy traffic.</p>
     *
     * <p><b>Serialization Strategy:</b></p>
     * <ul>
     *     <li><b>Keys:</b> Uses {@link StringRedisSerializer} for readable Redis
     *         topic/channel names.</li>
     *
     *     <li><b>Values:</b> Uses {@link StringRedisSerializer} so published
     *         payloads are transmitted as raw UTF-8 strings.</li>
     *
     *     <li><b>Performance Benefit:</b> Avoids Jackson object mapping and JSON
     *         serialization cost during publish/subscribe operations.</li>
     *
     *     <li><b>Protocol Format:</b> Payloads are expected to follow the internal
     *         cluster transport contract using a reserved delimiter separator
     *         (for example: id, recipient, sender, flags, sessionId, stanza).</li>
     *
     *     <li><b>Best Use Case:</b> Ideal for Redis Pub/Sub where messages are
     *         transient routing signals rather than persistent structured records.</li>
     * </ul>
     *
     * <p><b>Example Published Payload:</b></p>
     * <pre>
     * stanza-123␟userA␟userB␟CHAT␟1␟session9␟&lt;message .../&gt;
     * </pre>
     *
     * <p><b>Important:</b></p>
     * All subscribers must parse the incoming string using the same delimiter
     * protocol and field ordering.</p>
     *
     * @return Configured {@link RedisTemplate} for cluster-wide string message broadcasting.
     */
    @Bean    
    public RedisTemplate<String, String> clusterStringRedisTemplate() {
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