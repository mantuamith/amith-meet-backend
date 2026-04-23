package com.algomeet.xmpp.chatservice.cluster.listener;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.routing.chat.CarbonCopyHandler;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.util.ClusterSyncProtocolUtil;

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
    private static final Pattern CLUSTER_MESSAGE_DELIMITER_PATTERN = Pattern.compile(String.valueOf(ClusterSyncProtocolUtil.SEP));
    
    private final LocalStanzaDispatcher localStanzaDispatcher;
    private final CarbonCopyHandler carbonCopyHandler;

    /**
     * Entry point for messages arriving from the cluster infrastructure (e.g., Redis Pub/Sub).
     * @param rawMessage The raw string representing the {@link ClusterSyncMessage}.
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
         *     <li>[0] version     - Sync protocol version</li>
         *     <li>[1] id          - Unique stanza/message ID</li>
         *     <li>[2] to          - Target UserKey or JID</li>
         *     <li>[3] from        - Sender UserKey or JID</li>
         *     <li>[4] chatType    - CHAT / GROUPCHAT / etc.</li>
         *     <li>[5] allowEcho   - "1" = true, "0" = false</li>
         *     <li>[6] sessionId   - Originating client session ID</li>
         *     <li>[7] shouldCarbon - "1" = true, "0" = false</li></li>
         *     <li>[8] payload     - Raw XMPP XML stanza</li>

         * </ol>
         *
         * <p>{@code ClusterSyncProtocolUtil.V1_FIELD_COUNT} should be set to {@code ClusterSyncProtocolUtil.V1_FIELD_COUNT} so the split
         * operation stops after the expected number of fields.</p>
         */
        String[] message =
                CLUSTER_MESSAGE_DELIMITER_PATTERN.split(rawMessage, ClusterSyncProtocolUtil.V1_FIELD_COUNT);

        /**
         * Validate decoded structure before processing.
         *
         * Defensive checks prevent:
         * - malformed cluster messages
         * - partial payloads
         * - protocol mismatch between publisher/subscriber versions
         * - ArrayIndexOutOfBoundsException
         */
        if (message != null 
        		&& message.length == ClusterSyncProtocolUtil.V1_FIELD_COUNT) {

        	String id = message[1];
        	String to = message[2];
        	String from = message[3];
        	String chatType = message[4];
        	boolean isAllowEcho = "1".equals(message[5]);
        	String userSessionId = message[6];
        	boolean shouldCarbon = "1".equals(message[7]);
        	String payload = message[8];
        	
            localStanzaDispatcher.dispatchLocally(
            		id,
            		to,
            		isAllowEcho,
            		userSessionId,
            		payload
            );

            /**
             * Message Carbons are only applicable to one-to-one chats.
             *
             * XEP-0280 carbon copies are used to synchronize direct messages
             * across the sender's other active devices (mobile, desktop, web).
             *
             * Example:
             * - User sends message from phone
             * - Desktop client receives a <sent/> carbon copy
             *
             * Group chats typically do not use sender-side carbons because
             * the room itself already broadcasts messages to participants.
             *
             * Therefore, only process carbon copy generation when the
             * message type is normal direct CHAT.
             */
            if (ChatType.CHAT.name().equals(chatType.trim())) {
                carbonCopyHandler.handleSentMessageCarbonCopy(
                        from,
                        userSessionId,
                        payload,
                        shouldCarbon
                );
            }

            log.info("Successfully processed cluster sync for Stanza ID: {}",
                id
            );
        }
    }   
}