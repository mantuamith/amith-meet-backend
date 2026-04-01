package com.algomeet.xmpp.chatservice.cluster.listener;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.routing.handler.LocalStanzaDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Subscriber/Listener responsible for receiving XMPP stanzas synchronized 
 * across the server cluster.</p>
 * 
 * <p>In a multi-node deployment, when a message is routed to a user not 
 * connected to the current node, it is published to a global cluster topic. 
 * This listener intercepts those messages, de-serializes the 
 * {@link ClusterSyncMessage} payload, and attempts local delivery.</p>
 * 
 * <p><b>Execution Flow:</b></p>
 * <ol>
 *     <li>Receives raw JSON from the cluster message broker (Redis/RabbitMQ).</li>
 *     <li>De-serializes the JSON into a {@code ClusterSyncMessage} DTO.</li>
 *     <li>Calls {@link LocalStanzaDispatcher} to check if the recipient 
 *         is physically connected to <i>this</i> specific server instance.</li>
 * </ol>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessageListener {	

    private final LocalStanzaDispatcher localStanzaDispatcher;

    /**
     * Entry point for messages arriving from the cluster infrastructure.
     * 
     * @param rawMessage The JSON string representing the {@link ClusterSyncMessage}.
     * @param channel    The name of the cluster topic or channel the message arrived on.
     */
    public void onMessage(String rawMessage, String channel) {
        log.debug("Received cluster sync message on channel [{}]: {}", channel, rawMessage);
        
        // De-serialize JSON message back to ClusterSyncMessage        
        ClusterSyncMessage message = convertToObject(rawMessage, ClusterSyncMessage.class);

        if (message != null) {
            // Hand off to the dispatcher. If the user is on this node, the message 
            // is pushed over the WebSocket; otherwise, the dispatcher ignores it.
            localStanzaDispatcher.dispatchLocally(
                message.getTo(), 
                message.getFrom(), 
                message.getId(), 
                message.getChatType(),
                message.getPayload()
            );
            
            log.info("Received cluster sync message ID {}", message.getId());
        }
    }

    /**
     * Utility to de-serialize JSON strings into specific Java types.
     * 
     * <p>Note: Uses {@code findAndRegisterModules()} to support Java 8+ 
     * Time API (Instant/LocalDateTime) which are common in Stanza timestamps.</p>
     * 
     * @param json The JSON input string.
     * @param t    The class type to map the JSON to.
     * @param <T>  The generic type of the destination object.
     * @return The de-serialized object, or null if an error occurred.
     */
    private <T> T convertToObject(String json, Class<T> t) {
        try {
            // Configuration is performed inline for isolation; 
            // Consideration: Move ObjectMapper to a @Bean for better performance.
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); 
            return mapper.readValue(json, t);
        } catch (Exception ex) {
            log.error("Failed to de-serialize cluster message. Payload: {}, Error: {}", 
                json, ex.getMessage(), ex);
        }
        return null;
    }
}