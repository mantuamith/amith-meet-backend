package com.algomeet.signalservice.publisher;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.signalservice.dto.E2eeEvent;
import com.algomeet.signalservice.properties.RedisTopicProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Publisher responsible for broadcasting E2EE (End-to-End Encryption) events 
 * across the Algomeet server cluster using Redis Pub/Sub.</p>
 *
 * <p>In a distributed environment, security-related updates—such as new Signal PreKeys, 
 * device list synchronizations, or session resets—must be propagated to all nodes. 
 * This ensures that if a user is connected to <b>Node A</b>, but a security update 
 * for them is processed by <b>Node B</b>, the encryption state remains consistent 
 * across the entire fabric.</p>
 *
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 * <li>Encapsulating {@link E2eeEvent} payloads for cross-node distribution.</li>
 * <li>Interfacing with the Redis backbone to ensure high-availability of security signals.</li>
 * <li>Providing consistent logging and error handling for the E2EE synchronization layer.</li>
 * </ul>
 *
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class E2eeEventPublisher {

    /** Centralized configuration for Redis topic naming conventions, such as the E2EE event topic. */
    private final RedisTopicProperties redisTopicProperties;

    /** Shared template configured for JSON serialization of E2EE events to maintain cross-node compatibility. */
    private final RedisTemplate<String, E2eeEvent> redisTemplate;
    
    /**
     * <p>Publishes an E2EE event to the cluster-wide Redis topic.</p>
     *
     * <p>This method allows the Signal Service to broadcast critical security updates 
     * that other nodes must act upon, such as invalidating a local device cache 
     * or pushing a key update to a connected client session.</p>
     *
     * @param userKey The unique identifier (UUID) of the user whom this event concerns.
     * @param event   The {@link E2eeEvent} DTO containing the specific Signal protocol 
     * action (e.g., PREKEY_UPDATE) or data payload.
     * @throws RuntimeException if the underlying Redis transport layer encounters a 
     * connectivity issue or serialization error.
     */
    public void convertAndSend(UUID userKey, E2eeEvent event) {
        try {						
            String topic = redisTopicProperties.getE2eeEventTopic();
            
            log.info("Broadcasting E2EE event for user [{}] to topic [{}]", userKey, topic);

            // Broadcast the event to the global E2EE topic defined in properties
            redisTemplate.convertAndSend(topic, event);
            
        } catch (Exception ex) {
            log.error("Failed to publish E2EE event for user [{}]. Reason: {}", userKey, ex.getMessage());
            // Rethrown as RuntimeException to ensure compatibility with Spring's 
            // transaction management and to provide a clean exit for the calling thread.
            throw new RuntimeException("Error publishing E2EE event to Redis backbone", ex);
        }
    }
}