package com.algomeet.xmpp.chatservice.cluster.publisher;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.exceptions.ClusterMessageException;
import com.algomeet.xmpp.chatservice.properties.RedisTopicProperties;
import com.algomeet.xmpp.chatservice.util.ClusterSyncProtocolUtil;

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
public class ClusterMessagePublisher {
	/**
	 * Redis client used to publish {@link ClusterSyncMessage} objects
	 * to subscribed cluster nodes.
	 */
	private final RedisTemplate<String, String> redisTemplate;

	/**
	 * Configuration holder containing Redis topic names used by the system.
	 */
	private final RedisTopicProperties redisTopicProperties;

	public ClusterMessagePublisher(
			@Qualifier("clusterStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
			RedisTopicProperties redisTopicProperties
			) {
		this.redisTemplate = redisTemplate;
		this.redisTopicProperties = redisTopicProperties;
	}

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

		convertAndSendToUser(id, to, from, chatType, true, null, false, payload);
	}

	/**
	 * Convenience overload that resolves the originating session ID from the
	 * authenticated principal and disables carbon copy delivery.
	 *
	 * <p>When same-session echo is disabled, the sender's session ID is attached
	 * so receiving nodes can skip the originating device while still delivering
	 * to the user's other active sessions.</p>
	 *
	 * @param id          Unique stanza/message ID
	 * @param to          Recipient user key or JID
	 * @param from        Sender user key or JID
	 * @param chatType    CHAT / GROUPCHAT
	 * @param isAllowEcho Whether delivery back to the same session is allowed
	 * @param payload     Raw XML stanza payload
	 * @param principal   Authenticated XMPP session principal
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

	    // Include session ID only when same-session echo is disabled.
	    if (principal != null && !isAllowEcho) {
	        sessionId = principal.getSessionId();
	    }

	    convertAndSendToUser(id, to, from, chatType, isAllowEcho, sessionId, false, payload);
	}

	/**
	 * Convenience overload that resolves the originating session ID from the
	 * authenticated principal.
	 *
	 * <p>When same-session echo is disabled, the sender's session ID is attached
	 * so receiving nodes can skip the originating device while still delivering
	 * to the user's other active sessions.</p>
	 *
	 * @param id           Unique stanza/message ID
	 * @param to           Recipient user key or JID
	 * @param from         Sender user key or JID
	 * @param chatType     CHAT / GROUPCHAT
	 * @param isAllowEcho  Whether delivery back to the same session is allowed
	 * @param shouldCarbon Whether message is eligible for carbon copy handling
	 * @param payload      Raw XML stanza payload
	 * @param principal    Authenticated XMPP session principal
	 */
	public void convertAndSendToUser(
	        String id,
	        String to,
	        String from,
	        ChatType chatType,
	        Boolean isAllowEcho,
	        Boolean shouldCarbon,
	        String payload,
	        XmppPrincipal principal) {

	    String sessionId = null;

	    // Include session ID only when same-session echo is disabled.
	    if (principal != null && !isAllowEcho) {
	        sessionId = principal.getSessionId();
	    }

	    convertAndSendToUser(id, to, from, chatType, isAllowEcho, sessionId, shouldCarbon, payload);
	}
	
	/**
	 * Thread-local reusable buffer to avoid allocating StringBuilder per message.
	 *
	 * <p>This is safe because each thread (Netty worker / request thread)
	 * has its own isolated buffer instance.</p>
	 */
	private static final ThreadLocal<StringBuilder> BUFFER =
			ThreadLocal.withInitial(() -> new StringBuilder(512));

	private String buildClusterMessage(
			String version,
			String id,
			String to,
			String from,
			ChatType chatType,
			Boolean isAllowEcho,
			String sessionId,
			Boolean shouldCarbon,
			String payload) {

		StringBuilder sb = BUFFER.get();
		sb.setLength(0);

		char sep = ClusterSyncProtocolUtil.SEP;

		sb.append(version).append(sep)
		.append(id).append(sep)
		.append(to).append(sep)
		.append(from).append(sep)
		.append(chatType.name()).append(sep)
		.append(isAllowEcho ? "1" : "0").append(sep)
		.append(sessionId == null ? "" : sessionId).append(sep)
		.append(shouldCarbon ? "1" : "0").append(sep)
		.append(payload);

		return sb.toString();
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
			Boolean shouldCarbon,
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
			 * Build a compact transport message for Redis Pub/Sub using a lightweight
			 * delimiter-based protocol instead of JSON serialization.
			 *
			 * <p><b>Why use this approach:</b></p>
			 * <ul>
			 *     <li>Reduces object creation compared to DTO + JSON serialization.</li>
			 *     <li>Lower CPU overhead under heavy publish volume.</li>
			 *     <li>Smaller payload size than JSON.</li>
			 *     <li>Faster parsing on subscriber nodes.</li>
			 * </ul>
			 *
			 * <p><b>Field Order Contract (must remain consistent):</b></p>
			 * <ol>
			 * 	   <li>version      - Sync protocol version</li>
			 *     <li>id           - Unique stanza/message identifier</li>
			 *     <li>to           - Recipient UserKey or JID</li>
			 *     <li>from         - Sender UserKey or JID</li>
			 *     <li>chatType     - CHAT / GROUPCHAT / etc.</li>
			 *     <li>isAllowEcho  - "1" = true, "0" = false</li>
			 *     <li>sessionId    - Originating session for duplicate suppression</li>
			 *     <li>shouldCarbon - "1" = true, "0" = false</li></li>
			 *     <li>payload      - Raw XMPP XML stanza</li>
			 *      
			 * </ol>
			 */
			String msg = buildClusterMessage(
					ClusterSyncProtocolUtil.v1,
					id,
					to,
					from,
					chatType,
					isAllowEcho,
					sessionId,
					shouldCarbon,
					payload
					);

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
					redisTopicProperties.getClusterSync(),
					msg
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
					redisTopicProperties.getClusterSync(),
					ex.getMessage()
					);

			throw new ClusterMessageException(
					"Error publishing to redis topic",
					ex
					);
		}
	}
}