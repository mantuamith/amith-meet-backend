package com.algomeet.xmpp.chatservice.cluster.publisher;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.exceptions.ClusterMessageException;
import com.algomeet.xmpp.chatservice.properties.RedisTopicProperties;
import com.algomeet.xmpp.chatservice.util.ClusterSyncProtocolUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

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
@Component
public class ReactiveClusterMessagePublisher extends AbstractClusterMessagePublisher{

	public ReactiveClusterMessagePublisher(
			@Qualifier("reactiveStringRedisTemplate")
			ReactiveRedisTemplate<String, String> reactiveRedisTemplate,
			RedisTopicProperties redisTopicProperties) {
		this.reactiveRedisTemplate = reactiveRedisTemplate;
		this.redisTopicProperties = redisTopicProperties;
	}

	/**
	 * Redis client used to publish {@link ClusterSyncMessage} objects
	 * to subscribed cluster nodes.
	 */
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
	private final RedisTopicProperties redisTopicProperties;

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
	public Mono<Void> convertAndSendToUser(
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

		return convertAndSendToUser(id, to, from, chatType, isAllowEcho, sessionId, false, false, payload);
	}

	public Mono<Void> convertAndSendToUser(
			String id,
			String to,
			String from,
			ChatType chatType,
			Boolean isAllowEcho,
			Boolean shouldCarbon,
			Boolean isAckStanza,
			String payload,
			XmppPrincipal principal) {

		String sessionId = null;

		// Include session ID only when same-session echo is disabled.
		if (principal != null && !isAllowEcho) {
			sessionId = principal.getSessionId();
		}

		return convertAndSendToUser(id, to, from, chatType, isAllowEcho, sessionId, shouldCarbon, isAckStanza, payload);
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
	public Mono<Void> convertAndSendToUser(
			String id,
			String to,
			String from,
			ChatType chatType,
			Boolean isAllowEcho,
			Boolean shouldCarbon,
			String payload,
			String sessionId) {

		return convertAndSendToUser(id, to, from, chatType, isAllowEcho, sessionId, shouldCarbon, false, payload);
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
	public Mono<Void> convertAndSendToUser(
			String id,
			String to,
			String from,
			ChatType chatType,
			Boolean isAllowEcho,
			String sessionId,
			Boolean shouldCarbon,
			Boolean isAckStanza,
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
				id = UuidCreator.getTimeOrderedEpoch().toString();
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
			 *     <li>isAckStanza  - "1" = true, "0" = false</li></li>
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
					isAckStanza,
					payload
					);

			log.debug(
					"Broadcasting cluster sync for recipient [{}], stanzaId [{}], type [{}]",
					to, id, chatType
					);

			/**
			 * Publish to shared Redis topic.
			 */
			return publish(msg);

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
					ex.getMessage(),
					ex
					);

			throw new ClusterMessageException(
					"Error publishing to redis topic",
					ex
					);
		}
	}

	private Mono<Void> publish(String msg) {
		/**
		 * Publish to shared Redis topic.
		 *
		 * All cluster nodes listening on this topic will receive the event.
		 * Only the node that owns the recipient session typically performs
		 * the final socket delivery.
		 */
		return reactiveRedisTemplate.convertAndSend(
				redisTopicProperties.getClusterSync(),
				msg)
				.then();
	}
}