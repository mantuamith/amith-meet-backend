package com.algomeet.xmpp.chatservice.cluster.listener;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.util.ClusterUtil;
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
    private static final Pattern CLUSTER_MESSAGE_DELIMITER_PATTERN = Pattern.compile(String.valueOf(ClusterUtil.SEP));

    /**
     * Entry point for messages arriving from the cluster infrastructure (e.g., Redis Pub/Sub).
     * * @param rawMessage The raw JSON string representing the {@link ClusterSyncMessage}.
     * @param channel    The cluster-wide topic or channel name (e.g., 'xmpp.sync.stanzas').
     */
    public void onMessage(String rawMessage, String channel) {
        log.debug("Intercepted cluster sync message on channel [{}]: {}", channel, rawMessage);
                
        /**
         * Decode the compact cluster transport message received from Redis Pub/Sub.
         *
         * <p>The incoming {@code rawMessage} was previously encoded by the publisher
         * using a delimiter-based protocol instead of JSON for better performance.</p>
         *
         * <p><b>Why split with a fixed limit:</b></p>
         * <ul>
         *     <li>Preserves the final payload field even when the XML stanza contains
         *         special characters or separator-like content.</li>
         *     <li>Prevents unnecessary extra splits.</li>
         *     <li>Ensures stable parsing based on the known protocol contract.</li>
         * </ul>
         *
         * <p><b>Expected Message Format (7 fields):</b></p>
         * <ol>
         *     <li>[0] id          - Unique stanza/message ID</li>
         *     <li>[1] to          - Target UserKey or JID</li>
         *     <li>[2] from        - Sender UserKey or JID</li>
         *     <li>[3] chatType    - CHAT / GROUPCHAT / etc.</li>
         *     <li>[4] allowEcho   - "1" = true, "0" = false</li>
         *     <li>[5] sessionId   - Originating client session ID</li>
         *     <li>[6] payload     - Raw XMPP XML stanza</li>
         * </ol>
         *
         * <p>{@code ClusterUtil.MESSAGE_LENGTH} should be set to {@code 7} so the split
         * operation stops after the expected number of fields.</p>
         */
        String[] message =
                CLUSTER_MESSAGE_DELIMITER_PATTERN.split(rawMessage, ClusterUtil.MESSAGE_LENGTH);

        /**
         * Validate decoded structure before processing.
         *
         * Defensive checks prevent:
         * - malformed cluster messages
         * - partial payloads
         * - protocol mismatch between publisher/subscriber versions
         * - ArrayIndexOutOfBoundsException
         */
        if (message != null && message.length == ClusterUtil.MESSAGE_LENGTH) {

            /**
             * Forward the message to the local stanza dispatcher.
             *
             * This means the current node has received the cluster event and now
             * attempts delivery to sessions physically connected to this server.
             *
             * Parameters:
             *
             * message[0] = stanza/message ID
             * message[1] = recipient user key / JID
             * message[4] = allowEcho flag ("1" => true)
             * message[5] = originating session ID
             * message[6] = raw XML payload
             *
             * Note:
             * Fields [2] and [3] are not required here because local dispatch may only
             * need routing metadata + payload. They can still be retained for auditing
             * or future enhancements.
             */
            localStanzaDispatcher.dispatchLocally(
                    message[0],
                    message[1],
                    "1".equals(message[4]),
                    message[5],
                    message[6]
            );

            /**
             * Log successful processing for observability and tracing.
             *
             * Useful for:
             * - debugging cross-node delivery
             * - message flow correlation
             * - cluster health monitoring
             */
            log.info("Successfully processed cluster sync for Stanza ID: {}",
                message[0]
            );
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