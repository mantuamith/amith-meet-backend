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
 * <p>Cluster-wide Broadcaster for XMPP Stanzas using Redis Pub/Sub.</p>
 * * <p>In a distributed environment, users are spread across multiple signaling nodes. 
 * This component handles the 'fan-out' or 'routing-to-other-node' logic. When a 
 * stanza is received, it is encapsulated into a {@link ClusterSyncMessage} and 
 * broadcast to a global Redis topic. Every node in the cluster listens to this topic, 
 * allowing the node that physically holds the recipient's WebSocket to intercept 
 * and deliver the message.</p>
 * * <p><b>Core Workflow:</b></p>
 * <ul>
 * <li>Maps local XMPP routing intent into a cluster-sharable DTO.</li>
 * <li>Leverages Redis as a high-throughput messaging backbone for node-to-node signaling.</li>
 * <li>Ensures that synchronization stanzas (Carbons) reach all active sessions, 
 * regardless of which server instance they are connected to.</li>
 * </ul>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessagePublisher {

    private final RedisTopicProperties redisTopicProperties;
    private final RedisTemplate<String, ClusterSyncMessage> redisTemplate;
    
    /**
     * Helper method for standard direct message delivery without synchronization metadata.
     */
    public void convertAndSendToUser(String id, String to, String from, ChatType chatType, String payload) {
        convertAndSendToUser(id, to, from, chatType, false, null, payload);
    }
    
    /**
     * Publishes a stanza to the Redis cluster backbone for universal delivery.
     * * @param id           The unique Stanza ID (essential for XEP-0198 and client-side deduplication).
     * @param to           The target user's unique key/JID.
     * @param from         The originating sender's user key/JID.
     * @param chatType     The classification of the conversation (CHAT, GROUPCHAT, etc.).
     * @param isCarbonCopy Set to TRUE if this is an XEP-0280 sync message for other devices.
     * @param sessionId    The ID of the originating session (used for loop suppression on the receiving end).
     * @param payload      The raw XML stanza to be transmitted across the wire.
     * @throws ClusterMessageException if the Redis transport layer fails, potentially leading to delivery loss.
     */
    public void convertAndSendToUser(String id, String to, String from, ChatType chatType, Boolean isCarbonCopy, String sessionId, String payload) {
        try {						
            // Construct the DTO that acts as the envelope for cluster-wide routing
            ClusterSyncMessage message = ClusterSyncMessage.builder()
                    .id(id)
                    .to(to)					
                    .from(from)
                    .chatType(chatType)
                    .isCarbonCopy(isCarbonCopy)
                    .sessionId(sessionId)
                    .payload(payload)
                    .build();

            log.debug("Broadcasting cluster sync for Recipient [{}], Stanza ID: {}", to, id);

            // Publish to the Redis Topic. This is a non-blocking operation in terms of 
            // XMPP delivery, but critical for cluster consistency.
            redisTemplate.convertAndSend(redisTopicProperties.getClusterSyncTopic(), message);
            
        } catch (Exception ex) {
            // Failure here means a breakdown in the cluster communication backbone.
            log.error("CRITICAL: Failed to publish stanza {} to Redis cluster topic [{}]. Error: {}", 
                id, redisTopicProperties.getClusterSyncTopic(), ex.getMessage());
            throw new ClusterMessageException("Error publishing to redis topic", ex);
        }
    }
}