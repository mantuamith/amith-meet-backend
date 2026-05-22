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
		boolean isAckStanza = false;
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
			
			boolean isCountable = XmppStanzaUtil.isCountableMessage(originalXml);
			
			// Determine if message is ACK stanza
			isAckStanza = XmppStanzaUtil.isMessageAckStanza(originalXml);
			offlineMessageService.save(messageId, stanzaId, toUserKey, fromUserKey, type, isAckStanza, isCountable, forArchiveXml)
		            .doOnSuccess(saved -> {
		            	// Send an immediate server-level acknowledgment to the sender.
		            	//
		            	// This acknowledgment confirms that:
		            	// 1. The server has successfully received the stanza.
		            	// 2. The stanza has been persisted to the database.
		            	// 3. The server has taken full responsibility for further routing/delivery.
		            	//
		            	// This is a custom acknowledgment (not client XEP-0198 ack),
		            	// used to provide early delivery assurance back to the sender.
		            	XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), fromJid);	
		            	
		            	// Check if the message contains the XMPP Message Retraction namespace (XEP-0424 / urn:xmpp:message-retract:1)
		            	if (XmppStanzaUtil.isRetractStanza(originalXml)) {		            	    
		            	    // Extract the 'id' attribute from the <retract/> element, which refers to the original message to be deleted
		            	    String retractMessageId = xmppRetractUtil.getRetractMessageId(originalXml);

		            	    // Ensure the retract ID is valid and not empty before proceeding
		            	    if (StringUtils.hasText(retractMessageId)) {
		            	        
		            	        // Execute the deletion logic (checking permissions, removing from offline storage, and updating MAM)
		            	        processRetraction(retractMessageId, toUserKey, fromUserKey, principal).subscribe();
		            	    }
		            	}
						
						// --- XEP-0184: Message Delivery Receipts ---
					    // If the stanza contains the 'urn:xmpp:receipts' namespace, the recipient's 
					    // device has successfully received the message.
					    if (originalXml.contains(XmppReceiptUtil.NS_RECEIPTS)) {

					        String ackMessageId = xmppReceiptUtil.getAckMessageId(originalXml);
					        if (StringUtils.hasText(ackMessageId)) {
					        	
					        	// Clear the heavy XML payload from the offline buffer now that the client has acknowledged delivery.
					        	// We trigger this asynchronously and fire-and-forget; it does not block the main Netty/XMPP processing loop.
					        	offlineMessageService.clearOfflineStanza(UUID.fromString(principal.getUserKey()), UUID.fromString(ackMessageId))
					        	.doOnSuccess(unused -> log.debug("Successfully clear offline stanza for user: {} up to ID: {}", principal.getUserKey(), ackMessageId))
					        	.doOnError(error -> log.error("Failed to clear offline message database buffer for user: {}", principal.getUserKey(), error))
					        	.subscribe();
					        }
					    }
					    
					    // --- XEP-0333: Chat Markers (Read Receipts) ---
					    // If the stanza contains the 'urn:xmpp:chat-markers:0' namespace (displayed), 
					    // the user has actively viewed the conversation.
					    if (originalXml.contains(XmppReadUtil.NS_DISPLAYS)) {
					        String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);
					        
					        if (StringUtils.hasText(ackMessageId)) {
					            // Decrement the unread counter for this specific sender-recipient pair.
					            // Note: fromUserKey is the person who read it, toUserKey is the original sender.
					            unreadCountService.syncUnreadCount(toUserKey, fromUserKey, UUID.fromString(ackMessageId), principal)
					            .doOnSuccess(success -> {
					            	// Trigger a fire-and-forget background purge of processed/soft-deleted messages.
					            	offlineMessageService.purgeDeletedMessagesUpToCheckpoint(UUID.fromString(fromUserKey), UUID.fromString(toUserKey), messageId).subscribe();
					            })
					            .subscribe();
					        }
					    }
					    
					    if (isCountable) {
					    	// Asynchronous Unread Tracking
					    	// Increment the unread counter for the recipient (toUserKey) relative to the sender (fromUserKey).
					    	// This is handled reactively to avoid blocking the Netty event loop during DB writes.
					    	unreadCountService.incrementUnreadCount(fromUserKey, toUserKey)
					    	.doOnError(e -> {
					    		// Critical: Log storage failures specifically for audit trails 
					    		// in case of "lost" message notifications in production.
					    		log.error("Storage failure for incrementing unread count for message {}: {}", id, e.getMessage(), e);
					    	})
					    	// Use subscribe() to trigger the operation since the Netty pipeline 
					    	// does not natively await this Mono's completion.
					    	.subscribe();
					    }
					})
					.doOnError(e -> {					
						log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
						if (e instanceof DuplicateKeyException) {
							// Duplicate stanza detected (idempotent case).
							// Client MUST ignore this error; used only to support safe retries.
							xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(),
							        XmppErrorType.CANCEL,
							        XmppErrorConditions.DUPLICATE_KEY_ERROR,
							        "Stanza has duplicate key");
						} else {
							xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.WAIT, 
									XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
						}
					})
					.subscribe();		
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
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, false, shouldCarbon, isAckStanza,
				(isArchivable ? forArchiveXml : originalXml), principal);
						
		pushNotification(ctx, id, toUserKey, fromUserKey, type, originalXml, sessions, principal);
	}
	
	public Mono<Void> processRetraction(String retractId, String toUserKey, String fromUserKey, XmppPrincipal principal) {
		return offlineMessageService.findByIdAndSender(UUID.fromString(retractId), UUID.fromString(fromUserKey))
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
