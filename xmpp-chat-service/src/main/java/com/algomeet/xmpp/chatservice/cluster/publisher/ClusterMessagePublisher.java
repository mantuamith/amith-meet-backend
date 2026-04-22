package com.algomeet.xmpp.chatservice.cluster.publisher;

import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.exceptions.ClusterMessageException;
import com.algomeet.xmpp.chatservice.properties.RedisTopicProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Cluster-wide Broadcaster for XMPP stanzas using Redis Pub/Sub.</p>
 *
 * <p>In a horizontally scaled environment, users may be connected to different
 * application nodes. A stanza received on Node A may need to be delivered to a
 * recipient whose active session exists on Node B.</p>
 *
 * <p>This component converts the routing request into a
 * {@link ClusterSyncMessage} and publishes it to a shared Redis topic.
 * All nodes subscribed to that topic can inspect the message and the node
 * owning the destination session performs the actual WebSocket delivery.</p>
 *
 * <p><b>Main Responsibilities:</b></p>
 * <ul>
 *     <li>Bridge local routing into cluster-wide routing.</li>
 *     <li>Provide cross-node delivery for direct chat and group events.</li>
 *     <li>Support multi-session synchronization such as message echo/carbons.</li>
 *     <li>Decouple nodes using Redis as a lightweight signaling backbone.</li>
 * </ul>
 *
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessagePublisher {

    /**
     * Configuration holder containing Redis topic names used by the system.
     */
    private final RedisTopicProperties redisTopicProperties;

    /**
     * Redis client used to publish {@link ClusterSyncMessage} objects
     * to subscribed cluster nodes.
     */
    private final RedisTemplate<String, ClusterSyncMessage> redisTemplate;

    /**
     * Convenience overload for normal routing behavior.
     *
     * <p>Uses:
     * <ul>
     *     <li>allowEcho = true</li>
     *     <li>sessionId = null</li>
     * </ul>
     *
     * <p>This is commonly used when no sender-session filtering is required.</p>
     *
     * @param id        Unique stanza/message ID
     * @param to        Recipient user key or JID
     * @param from      Sender user key or JID
     * @param chatType  CHAT / GROUPCHAT
     * @param payload   Raw XML stanza payload
     */
    public void convertAndSendToUser(
            String id,
            String to,
            String from,
            ChatType chatType,
            String payload) {

        convertAndSendToUser(id, to, from, chatType, true, null, payload);
    }

    /**
     * Overload that derives the originating sessionId from the authenticated principal.
     *
     * <p>If echo is disabled, the sender session ID is attached so receiving nodes
     * can suppress delivery back to the originating device while still allowing
     * delivery to other sessions of the same user.</p>
     *
     * @param id           Unique stanza/message ID
     * @param to           Recipient user key or JID
     * @param from         Sender user key or JID
     * @param chatType     CHAT / GROUPCHAT
     * @param isAllowEcho  Whether same-session echo is allowed
     * @param payload      Raw XML stanza
     * @param principal    Authenticated session principal
     */
    public void convertAndSendToUser(
            String id,
            String to,
            String from,
            ChatType chatType,
            Boolean isAllowEcho,
            String payload,
            XmppPrincipal principal) {

        String sessionId = null;

        /**
         * When echo is not allowed, capture the originating session ID.
         *
         * Receiving nodes may use this to:
         * - skip sender's current device
         * - still deliver to sender's other devices
         * - avoid duplicate self-delivery
         */
        if (principal != null && !(isAllowEcho)) {
            sessionId = principal.getSessionId();
        }

        convertAndSendToUser(id, to, from, chatType, isAllowEcho, sessionId, payload);
    }

    /**
     * Publishes a cluster synchronization message to Redis.
     *
     * <p>This is the core method used for cross-node stanza routing.</p>
     *
     * <p><b>Flow:</b></p>
     * <ol>
     *     <li>Ensure message ID exists.</li>
     *     <li>Create {@link ClusterSyncMessage} envelope.</li>
     *     <li>Publish to Redis topic.</li>
     *     <li>Subscribed nodes process and locally deliver if applicable.</li>
     * </ol>
     *
     * @param id           Unique stanza ID. Auto-generated if blank.
     * @param to           Target user key or JID.
     * @param from         Sender user key or JID.
     * @param chatType     Message category such as CHAT or GROUPCHAT.
     * @param isAllowEcho  TRUE if same-origin session may also receive echo.
     * @param sessionId    Originating session ID used for duplicate suppression.
     * @param payload      Raw XML stanza payload.
     *
     * @throws ClusterMessageException if Redis publish fails.
     */
    public void convertAndSendToUser(
            String id,
            String to,
            String from,
            ChatType chatType,
            Boolean isAllowEcho,
            String sessionId,
            String payload) {

        try {

            /**
             * Guarantee every cluster message has a unique identifier.
             *
             * Important for:
             * - tracing
             * - deduplication
             * - acknowledgements
             * - debugging
             */
            if (!(StringUtils.hasText(id))) {
                id = UUID.randomUUID().toString();
            }

            /**
             * Build transport envelope consumed by other nodes.
             */
            ClusterSyncMessage message = ClusterSyncMessage.builder()
                    .id(id)
                    .to(to)
                    .from(from)
                    .chatType(chatType)
                    .isAllowEcho(isAllowEcho)
                    .sessionId(sessionId)
                    .payload(payload)
                    .build();

            log.debug(
                "Broadcasting cluster sync for recipient [{}], stanzaId [{}], type [{}]",
                to, id, chatType
            );

            /**
             * Publish to shared Redis topic.
             *
             * All cluster nodes listening on this topic will receive the event.
             * Only the node that owns the recipient session typically performs
             * the final socket delivery.
             */
            redisTemplate.convertAndSend(
                    redisTopicProperties.getClusterSyncTopic(),
                    message
            );

        } catch (Exception ex) {

            /**
             * A failure here means node-to-node communication is broken for this event.
             *
             * Depending on architecture, this may result in:
             * - delayed delivery
             * - missed message sync
             * - missing carbons / echoes
             * - cross-node routing failure
             */
            log.error("CRITICAL: Failed to publish stanza [{}] to Redis topic [{}]. Error: {}",
                id,
                redisTopicProperties.getClusterSyncTopic(),
                ex.getMessage()
            );

            throw new ClusterMessageException(
                "Error publishing to redis topic",
                ex
            );
        }
    }
}