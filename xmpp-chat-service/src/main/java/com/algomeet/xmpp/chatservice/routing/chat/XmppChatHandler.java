package com.algomeet.xmpp.chatservice.routing.chat;

import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.call.CallLifeCycleTracker;
import com.algomeet.xmpp.chatservice.routing.call.JingleNotificationHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.service.UnreadCountService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.XmppCustomStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppReadUtil;
import com.algomeet.xmpp.chatservice.util.XmppReceiptUtil;
import com.algomeet.xmpp.chatservice.util.XmppRetractUtil;
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@ChannelHandler.Sharable
@Component
@AllArgsConstructor
public class XmppChatHandler {
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService; 
	private final UserSessionRegistry userSessionRegistry;
	private final NotificationService notificationService;
	private final JingleNotificationHandler jingleNotificationHandler;
	private final CallLifeCycleTracker callTracker;
	private final DomainProperties domainProperties;
	private final UnreadCountService unreadCountService;
	private final XmppReceiptUtil xmppReceiptUtil;
	private final XmppReadUtil xmppReadUtil;
	private final XmppUtil xmppUtil;
	private final XmppRetractUtil xmppRetractUtil;
	
	// Define a dedicated thread pool for your database work so Netty doesn't starve
	private static final Scheduler DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-chat-db-workers");

	/**
	 * Handles 1-to-1 message routing, persistence for offline storage, 
	 * and cluster-wide synchronization, and push notifications for offline users.
	 */
	public void handleDirectChatRouting(ChannelHandlerContext ctx, String id, String toJid, String fromJid, String type, String originalXml) {
		XmppMessageType msgType = XmppMessageType.fromString(type);
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();               

		String toUserKey = XmppUtil.getUserKey(toJid);
		String fromUserKey = principal.getUserKey();
		
		String forArchiveXml = originalXml;
		
		// Get user sessions from redis
		Set<UserSession> sessions = userSessionRegistry.getSessions(toUserKey);

		// Persistence & XEP-0198 Acknowledgment
		boolean isArchivable = XmppStanzaUtil.isArchivable(originalXml);
		// Determine if message is ACK stanza
		boolean isAckStanza = XmppStanzaUtil.isMessageAckStanza(originalXml);
		
		if (msgType.supportsOfflineStorage() && isArchivable) {	
			 /**
	         * Generate a monotonic UUIDv7 used as the stanza-id value.
	         *
	         * Why UUIDv7:
	         * - time-sortable (better than UUID for message ordering)
	         * - globally unique under distributed systems
	         * - suitable for MAM storage and pagination cursors
	         *
	         * Note: Lowercasing ensures consistency across storage/query layers.
	         */
	        UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
			// Insert stanza ID
			forArchiveXml = XmppStanzaUtil.insertStanzaId(originalXml, stanzaId.toString(), principal.getDomain());
			
			UUID messageId = StringUtils.hasText(id) 
				    ? UUID.fromString(id.trim()) 
				    : UuidCreator.getTimeOrderedEpoch();
			
			boolean isCountable = XmppCustomStanzaUtil.isCountableMessage(originalXml);
			
			final String archivedXml = forArchiveXml;			
			offlineMessageService.save(messageId, stanzaId, toUserKey, fromUserKey, type, isAckStanza, isCountable, forArchiveXml)
			.flatMap(saved -> {
				// Return server acknowledgment execution context
				// Send an immediate server-level acknowledgment to the sender.
            	//
            	// This acknowledgment confirms that:
            	// 1. The server has successfully received the stanza.
            	// 2. The stanza has been persisted to the database.
            	// 3. The server has taken full responsibility for further routing/delivery.
            	//
            	// This is a custom acknowledgment (not client XEP-0198 ack),
            	// used to provide early delivery assurance back to the sender.
				XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), stanzaId.toString()); 

				if (isAckStanza) {
					/*
	                 * CRITICAL TIMING SEQUENCE:
	                 * This event must execute strictly AFTER the underlying database write has fully 
	                 * completed to prevent "ghost data" or race conditions. 
	                 * Sending this cluster broadcast notifies downstream microservices or websocket 
	                 * connections to trigger cleanups (like messages sent to client acknowledgments). 
	                 * If sent before a guaranteed DB commit, cleanup actions might execute too early 
	                 * and target data that hasn't physically settled in the collection yet.
	                 * ACKs cleanup logic under ClusterMessageListener.onMessage().
	                 */
					clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, false, true, isAckStanza, archivedXml, principal);
				}

				// Create a list of dependent reactive tasks that run sequentially or in parallel safely
				Mono<Void> postSaveTasks = Mono.empty();

				// 1. Process Retractions safely
				// Check if the message contains the XMPP Message Retraction namespace (XEP-0424 / urn:xmpp:message-retract:1)
				if (XmppStanzaUtil.isRetractStanza(originalXml)) {
					String retractMessageId = xmppRetractUtil.getRetractMessageId(originalXml);
					if (StringUtils.hasText(retractMessageId)) {
						postSaveTasks = postSaveTasks.then(processRetraction(retractMessageId, toUserKey, fromUserKey, principal));
					}
				}

				// 2. Handle Message Delivery Receipts safely
				// --- XEP-0184: Message Delivery Receipts ---
			    // If the stanza contains the 'urn:xmpp:receipts' namespace, the recipient's 
			    // device has successfully received the message.
				if (originalXml.contains(XmppReceiptUtil.NS_RECEIPTS)) {
					String ackMessageId = xmppReceiptUtil.getAckMessageId(originalXml);
					if (StringUtils.hasText(ackMessageId)) {
						postSaveTasks = postSaveTasks.then(
								offlineMessageService.clearOfflineStanza(UUID.fromString(principal.getUserKey()), UUID.fromString(ackMessageId))
								.doOnError(ex -> log.error("Failed to clear offline message buffer for user: {}", principal.getUserKey(), ex))
								.onErrorResume(ex -> Mono.empty()) // Prevent sub-errors from breaking the chain
								);
					}
				}

				// 3. Handle Chat Markers (Read Receipts) cleanly via sequential flattening
				// --- XEP-0333: Chat Markers (Read Receipts) ---
			    // If the stanza contains the 'urn:xmpp:chat-markers:0' namespace (displayed), 
			    // the user has actively viewed the conversation.
				if (originalXml.contains(XmppReadUtil.NS_DISPLAYS)) {
					String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);
					if (StringUtils.hasText(ackMessageId)) {
						postSaveTasks = postSaveTasks.then(
								unreadCountService.syncUnreadCount(UUID.fromString(toUserKey), UUID.fromString(fromUserKey), UUID.fromString(ackMessageId))
								.flatMap(success -> offlineMessageService.purgeDeletedMessagesUpToCheckpoint(UUID.fromString(fromUserKey), UUID.fromString(toUserKey), success.getLastReadSid()))
								.onErrorResume(ex -> Mono.empty())
								);
					}
				}

				// 4. Handle Countable Increment cleanly
				if (isCountable) {
					// Asynchronous Unread Tracking
			    	// Increment the unread counter for the recipient (toUserKey) relative to the sender (fromUserKey).
			    	// This is handled reactively to avoid blocking the Netty event loop during DB writes.
					postSaveTasks = postSaveTasks.then(
							unreadCountService.incrementUnreadCount(fromUserKey, toUserKey)
							.doOnError(e -> log.error("Storage failure for incrementing unread count for message {}: {}", id, e.getMessage()))
							.onErrorResume(ex -> Mono.empty())
							).then();
				}

				return postSaveTasks;
			})
			.subscribeOn(DB_SCHEDULER) // Shifting execution away from Netty Event Loop
			.doOnError(e -> {                  
				log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
				if (e instanceof DuplicateKeyException) {
					xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.CANCEL, XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key");
				} else {
					xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.WAIT, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
				}
			})
			.subscribe(); // Single, managed entry point subscription
		}

		// Handle call life cycle 
		if (XmppMessageType.SET == XmppMessageType.fromString(type) 
				&& originalXml.contains("urn:xmpp:jingle:1")) {
			callTracker.track(ctx, toJid, fromJid, originalXml, principal);
		}   				

		// Check if carbon copy is required, if archivable the Carbon Copy is required
		Boolean shouldCarbon = isArchivable;
		
		// Broadast to Redis: Even if they are AWAY/DND, we attempt delivery 
		// to their active WebSocket channels across the cluster.
		if(!isAckStanza) {
			clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, false, shouldCarbon, isAckStanza,
					(isArchivable ? forArchiveXml : originalXml), principal);

			pushNotification(ctx, id, toUserKey, fromUserKey, type, originalXml, sessions, principal);
		}
	}
	
	public Mono<Void> processRetraction(String retractId, String toUserKey, String fromUserKey, XmppPrincipal principal) {
		return offlineMessageService.findByMessageIdAndSender(UUID.fromString(retractId), UUID.fromString(fromUserKey))
				.flatMap(message -> {

					// Decrement the unread counter for this specific sender-recipient pair.
					// Note: fromUserKey is the person who read it, toUserKey is the original sender.
					unreadCountService.decrementUnreadCount(toUserKey, fromUserKey, UUID.fromString(retractId), principal).subscribe();

					// Scenario: Record found, proceed to soft delete
					log.info("Message found, soft deleting offline record by emptying the body of the message: {}", retractId);
					String newString = "<body>This message was deleted</body>";
					message.setStanzaXml(XmppStanzaUtil.markAsRetractedStanza(message.getStanzaXml(), newString));
					message.setCountable(false);
					return offlineMessageService.save(message)
							.then();
				});
	}

	private void pushNotification(ChannelHandlerContext ctx,
			String id,
			String toUserKey,
			String fromUserKey,
			String type,
			String xml,
			Set<UserSession> sessions,
			XmppPrincipal principal) {

		boolean hasActive = !CollectionUtils.isEmpty(sessions) &&
				sessions.stream().anyMatch(s -> UserState.ACTIVE == s.getState());

		// Push Notification Logic
		// Trigger push if the user has no sessions OR no session is currently 'ACTIVE'
		if (!hasActive) {
			log.debug("User {} has no active sessions. Triggering push notification.", toUserKey);

			/*
			 * Jingle Signaling Detection (XEP-0166)
			 * 
			 * - 'urn:xmpp:jingle:1': Ensures the stanza belongs to the Jingle namespace.
			 */
			if (XmppMessageType.SET == XmppMessageType.fromString(type)
					&& xml.contains("urn:xmpp:jingle:1")) {   

				// Handle Jingle Signaling notification
				jingleNotificationHandler.handlePush(ctx, id, toUserKey, fromUserKey, xml, principal);

			} else {                 
				/*
				 * Standard Message Handling
				 * If the stanza is not a call initiation, treat it as a standard chat message.
				 * We extract the <body> element and trigger a Push Notification (FCM/APNs)
				 * to the recipient, ensuring they receive the message even if offline.
				 */            	
				if (XmppStanzaUtil.isArchivable(xml)) {
					String body = XmppUtil.getMessageBody(xml);
					sendPushNotification(toUserKey, body, NotificationType.DIRECT_MESSAGE, principal);
				}
			}     
		}
	}  
		
	/**
	 * Used to send push notification for new message
	 *
	 * @param toKey
	 * @param message
	 * @param notifcationType
	 */
	private void sendPushNotification(String toKey, String message, NotificationType notifcationType, XmppPrincipal principal) {
		Notification notif = Notification.builder()
				.receiverIds(Set.of(toKey))
				.type(notifcationType)
				.title("You have new message")
				.body(message)
				.tenantId(principal.getTenantId())
				.build();

		notificationService.sendPush(notif);
	}
}
