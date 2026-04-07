package com.algomeet.xmpp.chatservice.cluster.publisher;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.exceptions.ClusterMessageException;
import com.algomeet.xmpp.chatservice.properties.RedisTopicProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Publisher responsible for broadcasting XMPP stanzas across the server cluster 
 * using Redis Pub/Sub.</p>
 * 
 * <p>When a message is received by a server node, it may not know which node the 
 * recipient is physically connected to. This component wraps the stanza into a 
 * {@link ClusterSyncMessage} and publishes it to a shared Redis topic. All 
 * subscribed nodes will then receive the message via their 
 * {@code E2eeEventMessageListener}.</p>
 * 
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 *     <li>Encapsulating routing metadata (to, from, id) and the XML payload.</li>
 *     <li>Interfacing with Redis infrastructure to ensure cluster-wide visibility.</li>
 *     <li>Providing a fail-fast mechanism via {@link ClusterMessageException} if 
 *         the transport layer is unavailable.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessagePublisher {
	private final RedisTopicProperties redisTopicProperties;
    private final RedisTemplate<String, ClusterSyncMessage> redisTemplate;
    
    
    /**
     * Publishes a direct chat stanza to the cluster topic for user-specific delivery.
     * 
     * @param id       The unique Stanza ID (used for tracking/acks).
     * @param to       The recipient's User Key.
     * @param from     The sender's User Key.
     * @param chatType The chat type / conversation type
     * @param payload  The raw XML stanza content to be synchronized.
     * @throws ClusterMessageException if the message fails to publish to the Redis backbone.
     */
    public void convertAndSendToUser(String id, String to, String from, ChatType chatType, String payload) {
        try {						
            ClusterSyncMessage message = ClusterSyncMessage.builder()
                    .id(id)
                    .to(to)					
                    .from(from)
                    .chatType(chatType)
                    .payload(payload)
                    .build();

            log.info("Publishing cluster sync message for user [{}]: with Message ID: {}", to, id);

            // Broadcast to the global topic defined in the Redis configuration
            redisTemplate.convertAndSend(redisTopicProperties.getClusterSyncTopic(), message);
        } catch (Exception ex) {
            log.error("Failed to publish message {} to Redis cluster topic: {}", id, ex.getMessage());
            throw new ClusterMessageException("Error publishing to redis topic", ex);
        }
    }
}