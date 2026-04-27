package com.algomeet.xmpp.chatservice.routing.chat;

import java.util.Set;

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
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
		if (msgType.supportsOfflineStorage() && isArchivable) {	
			 /**
	         * Generate a monotonic ULID used as the stanza-id value.
	         *
	         * Why ULID:
	         * - time-sortable (better than UUID for message ordering)
	         * - globally unique under distributed systems
	         * - suitable for MAM storage and pagination cursors
	         *
	         * Note: Lowercasing ensures consistency across storage/query layers.
	         */
	        String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
			// Insert stanza ID
			forArchiveXml = XmppStanzaUtil.insertStanzaId(originalXml, ulidString, principal.getDomain());
					    
			offlineMessageService.save(id, toUserKey, fromUserKey, type, forArchiveXml)
		            .doOnSuccess(saved -> {
		            	boolean isAckMessage = false;
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
						
						// --- XEP-0184: Message Delivery Receipts ---
					    // If the stanza contains the 'urn:xmpp:receipts' namespace, the recipient's 
					    // device has successfully received the message.
					    if (originalXml.contains(XmppReceiptUtil.NS_RECEIPTS)) {
					    	isAckMessage = true;
					        String ackMessageId = xmppReceiptUtil.getAckMessageId(originalXml);

					        if (StringUtils.hasText(ackMessageId)) {
					            // Once delivery is confirmed, the message is no longer "offline" 
					            // and can be safely removed from the temporary offline storage.
					            offlineMessageService.deleteById(ackMessageId).subscribe();
					        }
					    }
					    
					    // --- XEP-0333: Chat Markers (Read Receipts) ---
					    // If the stanza contains the 'urn:xmpp:chat-markers:0' namespace (displayed), 
					    // the user has actively viewed the conversation.
					    if (originalXml.contains(XmppReadUtil.NS_DISPLAYS)) {
					    	isAckMessage = true;
					        String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);
					        
					        if (StringUtils.hasText(ackMessageId)) {
					            // Decrement the unread counter for this specific sender-recipient pair.
					            // Note: fromUserKey is the person who read it, toUserKey is the original sender.
					            unreadCountService.decrementUnreadCount(toUserKey, fromUserKey, principal).subscribe();
					        }
					    }
					    
					    if (!isAckMessage) {
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

		// Check if carbon copy is required, if archivable the Carbon Copy required
		Boolean shouldCarbon = isArchivable;
		
		// Broadast to Redis: Even if they are AWAY/DND, we attempt delivery 
		// to their active WebSocket channels across the cluster.
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, ChatType.CHAT, false, shouldCarbon, 
				(isArchivable ? forArchiveXml : originalXml), principal);
						
		pushNotification(ctx, id, toUserKey, fromUserKey, type, originalXml, sessions, principal);
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
