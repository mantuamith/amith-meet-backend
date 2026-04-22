package com.algomeet.xmpp.chatservice.cluster.listener;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Subscriber/Listener responsible for inter-node XMPP stanza synchronization.</p>
 * * <p>In a distributed AlgoMeet deployment, a user's WebSocket session resides on a 
 * single specific server node. When a stanza (Message, Carbon, or Sync) is routed 
 * to a user not connected to the originating node, the server publishes a 
 * {@link ClusterSyncMessage} to a global Redis/PubSub topic.</p>
 * * <p>This listener acts as the "Receiver" on every node in the cluster, ensuring 
 * that no matter which server holds the physical TCP/WebSocket connection, the 
 * message is delivered seamlessly.</p>
 * * <p><b>Execution Flow:</b></p>
 * <ol>
 * <li>Intercepts the broadcasted JSON payload from the cluster backbone.</li>
 * <li>De-serializes the payload into a structured {@code ClusterSyncMessage}.</li>
 * <li>Invokes {@link LocalStanzaDispatcher} to perform a local Registry lookup 
 * and push the data to the client if the session is present on this node.</li>
 * </ol>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessageListener {	

    private final LocalStanzaDispatcher localStanzaDispatcher;

    /**
     * Entry point for messages arriving from the cluster infrastructure (e.g., Redis Pub/Sub).
     * * @param rawMessage The raw JSON string representing the {@link ClusterSyncMessage}.
     * @param channel    The cluster-wide topic or channel name (e.g., 'xmpp.sync.stanzas').
     */
    public void onMessage(String rawMessage, String channel) {
        log.debug("Intercepted cluster sync message on channel [{}]: {}", channel, rawMessage);
        
        // Convert the wire-format JSON back into a typed DTO
        ClusterSyncMessage message = convertToObject(rawMessage, ClusterSyncMessage.class);

        if (message != null) {
            // Hand off to the local dispatcher. 
            // The dispatcher performs a non-blocking lookup in the LocalChannelRegistry.
            // If the 'to' JID matches a local session, the XML is pushed over the WebSocket.
            localStanzaDispatcher.dispatchLocally(
                message.getTo(), 
                message.getId(), 
                message.isAllowEcho(),
                message.getSessionId(),
                message.getPayload()                
            );
            
            log.info("Successfully processed cluster sync for Stanza ID: {}", message.getId());
        }
    }

    /**
     * Internal utility to de-serialize JSON strings into DTOs.
     * * <p><b>Performance Note:</b> Current implementation instantiates a new 
     * {@link ObjectMapper} per call. In high-throughput production environments, 
     * this should be replaced with a shared, thread-safe Bean to reduce 
     * GC pressure and improve latency.</p>
     * * @param json The raw JSON payload.
     * @param t    The target Class type.
     * @param <T>  The generic type.
     * @return The de-serialized object, or null if parsing fails.
     */
    private <T> T convertToObject(String json, Class<T> t) {
        try {
            // findAndRegisterModules() ensures support for JSR-310 (Instant/LocalDateTime)
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); 
            return mapper.readValue(json, t);
        } catch (Exception ex) {
            log.error("Critical: Failed to de-serialize cluster message. Data: {}, Error: {}", 
                json, ex.getMessage(), ex);
        }
        return null;
    }
}