package com.algomeet.xmpp.chatservice.routing.chat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.MediaClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.BatchMediaShareRequest;
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
import com.algomeet.xmpp.chatservice.stanza.parser.MediaReferenceParser;
import com.algomeet.xmpp.chatservice.util.XmppCustomStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppReadUtil;
import com.algomeet.xmpp.chatservice.util.XmppReceiptUtil;
import com.algomeet.xmpp.chatservice.util.XmppRetractUtil;
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import feign.FeignException;
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
	private final MediaClient mediaClient;
	
	// Define a dedicated thread pool for your database work so Netty doesn't starve
	// Pool A: Dedicated ONLY to non-blocking or fast reactive DB tracking/save orchestration
	private static final Scheduler CHAT_DB_SCHEDULER = 
	        Schedulers.newBoundedElastic(64, 20000, "xmpp-chat-db");

	// Pool B: Dedicated exclusively to isolating heavy blocking network I/O calls (Feign Clients)
	private static final Scheduler MEDIA_IO_SCHEDULER = 
	        Schedulers.newBoundedElastic(150, 5000, "xmpp-media-io");

	/**
	 * Handles 1-to-1 message routing, persistence for offline storage, 
	 * and cluster-wide synchronization, and push notifications for offline users.
	 * * @return A Mono<Void> that completes when storage, routing, and out-of-band notifications are finished.
	 */
	public Mono<Void> handleDirectChatRouting(ChannelHandlerContext ctx, String id, String toJid, String fromJid, String type, String originalXml) {
		XmppMessageType msgType = XmppMessageType.fromString(type);
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();               

		String toUserKey = XmppUtil.getUserKey(toJid);
		String fromUserKey = principal.getUserKey();
		
		String forArchiveXml = originalXml;
				
		// Persistence & XEP-0198 Acknowledgment
		boolean isArchivable = XmppStanzaUtil.isArchivable(originalXml);
		// Determine if message is ACK stanza
		boolean isAckStanza = XmppStanzaUtil.isMessageAckStanza(originalXml);
		
		Mono<Void> processingPipeline = Mono.empty();
		
		if (msgType.supportsOfflineStorage() && isArchivable) {	
			// Check for message file attachments
			List<UUID> mediaIds = null;
			try {
				mediaIds = MediaReferenceParser.extractMediaIds(originalXml);

				if (!CollectionUtils.isEmpty(mediaIds)) {
					BatchMediaShareRequest request = new BatchMediaShareRequest();
					request.setMediaIds(mediaIds.stream()
							.map(mid -> mid.toString())
							.collect(Collectors.toSet()));

					request.setShareWithUserKeys(List.of(fromUserKey, toUserKey));
					request.setMessageId(UUID.fromString(id));			
					
					// FIX: Defer the blocking media client call securely onto the DB worker pool
					processingPipeline = Mono.fromCallable(() -> shareMedias(fromUserKey, request))
							.subscribeOn(MEDIA_IO_SCHEDULER)
							.flatMap(shared -> {
								if (!shared) {
									xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.CANCEL, 
											XmppErrorConditions.INTERNAL_SERVER_ERROR, "Error sharing media file(s)");
									return Mono.error(new RuntimeException("Media sharing failed"));
								}
								return Mono.empty();
							});
				}				
			} catch(Exception ex) {
				log.error("Error parsing media references {}", originalXml, ex);
				xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.CANCEL, 
						XmppErrorConditions.INTERNAL_SERVER_ERROR, "Error parsing media file(s)");
				
				return Mono.empty();
			}			
			
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
			
			processingPipeline = processingPipeline.then(
				offlineMessageService.save(messageId, stanzaId, toUserKey, fromUserKey, type, isAckStanza, 
						isCountable, archivedXml, mediaIds, principal.getSessionId())
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
					XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), stanzaId.toString(), saved.retentionDays()); 

					// Replace the old sequential "postSaveTasks = Mono.empty()" pattern with a structured list
		            List<Mono<Void>> tasks = new java.util.ArrayList<>();
		            
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
						tasks.add(clusterMessagePublisher.convertAndSendToUser(id, toUserKey, fromUserKey, 
								ChatType.CHAT, false, true, isAckStanza, archivedXml, principal));
					}
					
		            // 1. Process Retractions
		            // Check if the message contains the XMPP Message Retraction namespace (XEP-0424 / urn:xmpp:message-retract:1)
		            if (XmppStanzaUtil.isRetractStanza(originalXml)) {
		                String retractMessageId = xmppRetractUtil.getRetractMessageId(originalXml);
		                if (StringUtils.hasText(retractMessageId)) {
		                    tasks.add(processRetraction(retractMessageId, toUserKey, fromUserKey, principal));
		                }
		            }

		            // 2. Handle Message Delivery Receipts
					// --- XEP-0184: Message Delivery Receipts ---
				    // If the stanza contains the 'urn:xmpp:receipts' namespace, the recipient's 
				    // device has successfully received the message.
		            if (originalXml.contains(XmppReceiptUtil.NS_RECEIPTS)) {
		                String ackMessageId = xmppReceiptUtil.getAckMessageId(originalXml);
		                if (StringUtils.hasText(ackMessageId)) {
		                    tasks.add(offlineMessageService.clearOfflineStanza(UUID.fromString(principal.getUserKey()), UUID.fromString(ackMessageId))
		                            .doOnError(ex -> log.error("Failed to clear offline message buffer for user: {}", principal.getUserKey(), ex))
		                            .onErrorResume(ex -> Mono.empty()));
		                }
		            }


		            // 3. Handle Chat Markers (Read Receipts) cleanly via sequential flattening
					// --- XEP-0333: Chat Markers (Read Receipts) ---
				    // If the stanza contains the 'urn:xmpp:chat-markers:0' namespace (displayed), 
				    // the user has actively viewed the conversation.
		            if (originalXml.contains(XmppReadUtil.NS_DISPLAYS)) {
		                String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);
		                
		                // Used to trace the count bug                
		                if (StringUtils.hasText(ackMessageId)) {
		                    Mono<Void> readReceiptTask = unreadCountService.syncUnreadCount(UUID.fromString(toUserKey), UUID.fromString(fromUserKey), UUID.fromString(ackMessageId))
		                            // Safely switch to purge logic ONLY if sync returns a valid data model
		                            .flatMap(success -> {
		                            	 // Used to trace the count bug
		                            	log.debug("success.getLastReadSid() {}", success.getLastReadSid());
		                                if (success == null || success.getLastReadSid() == null) {
		                                    return Mono.empty();
		                                }
		                                return offlineMessageService.purgeDeletedMessagesUpToCheckpoint(UUID.fromString(toUserKey), UUID.fromString(fromUserKey), success.getLastReadSid());
		                            })
		                            .doOnError(ex -> log.error("Error updating read markers or purging messages", ex))
		                            .onErrorResume(ex -> Mono.empty()); // Isolates the error so it doesn't break other tasks
		                    
		                    tasks.add(readReceiptTask);
		                }
		            }

		            // 4. Handle Countable Increment
		            if (isCountable) {
		            	// Asynchronous Unread Tracking
				    	// Increment the unread counter for the recipient (toUserKey) relative to the sender (fromUserKey).
				    	// This is handled reactively to avoid blocking the Netty event loop during DB writes.
		                Mono<Void> incrementTask = unreadCountService.incrementUnreadCount(fromUserKey, toUserKey)
		                        .doOnError(e -> log.error("Storage failure for incrementing unread count: {}", e.getMessage()))
		                        .onErrorResume(ex -> Mono.empty())
		                        .then();
		                tasks.add(incrementTask);
		            }

		            // Execute ALL gathered tasks asynchronously and concurrently
		            return Mono.when(tasks);
				})
				.subscribeOn(CHAT_DB_SCHEDULER) // Shifting execution away from Netty Event Loop
				.doOnError(e -> {                  
					log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
					if (e instanceof DuplicateKeyException) {
						xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.CANCEL, XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key");
					} else {
						xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.WAIT, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
					}
				})
			);
		}

		// Handle call life cycle 
		if (XmppMessageType.SET == XmppMessageType.fromString(type) 
				&& originalXml.contains(Constants.NS_JINGLE)) {
			processingPipeline = processingPipeline.then(callTracker.track(ctx, toJid, fromJid, originalXml, principal));
		}   				

		// Check if carbon copy is required, if archivable the Carbon Copy is required
		Boolean shouldCarbon = isArchivable;
		final String finalArchiveXml = forArchiveXml;
		
		// Broadcast to Redis and handle push updates reactively
		return processingPipeline.then(Mono.defer(() -> {
		    if (!isAckStanza) {
		        String payload = isArchivable ? finalArchiveXml : originalXml;
		        
		        // Asynchronously retrieve user sessions, fallback to empty set on error
		        return userSessionRegistry.getSessions(toUserKey)
		            .defaultIfEmpty(java.util.Collections.emptySet())
		            .flatMap(sessions -> {
		                // Ensure a safe fallback if the set itself is explicitly null
		                Set<UserSession> activeSessions = (sessions != null) ? sessions : java.util.Collections.emptySet();

		                // 1. Fire the native reactive cluster publisher (Always runs)
		                return clusterMessagePublisher.convertAndSendToUser(
		                        id, toUserKey, fromUserKey, ChatType.CHAT, false, shouldCarbon, isAckStanza, payload, principal
		                    )
		                    // 2. Chain seamlessly into the push notification method
		                    .then(pushNotification(ctx, id, toUserKey, fromUserKey, type, originalXml, activeSessions, principal));
		            });
		    }
		    return Mono.empty();
		}))
		.onErrorResume(err -> {
		    log.error("Error executing post-routing actions for message ID: {}", id, err);
		    return Mono.empty();
		})
		.then();
	}
	
	private boolean shareMedias(String fromUserKey, BatchMediaShareRequest request) {
	    int retryCounter = 0;
	    while (++retryCounter <= 3) {
	        try {
	            log.info("Attempt {} to share media batch for user: {}", retryCounter, fromUserKey);
	            mediaClient.batchShare(fromUserKey, request);
	            return true;
	        } catch (FeignException ex) {
	            int status = ex.status();
	            log.error("Feign error sharing media files {}. HTTP Status: {} | Message: {}", 
	                    request.getMediaIds(), status, ex.getMessage());

	            // Fail fast on client errors (4xx) except for specific transient issues like 408 (Timeout) or 429 (Too Many Requests)
	            if (status >= 400 && status < 500 && status != 408 && status != 429) {
	                log.error("Client error encountered ({}). Aborting retries.", status);
	                break;
	            }
	            
	            // For 5xx server errors, 408 timeouts, or 429 rate limits, let the loop continue and retry.
	        } catch (Exception ex) {
	            // Fallback catch for unexpected infrastructure issues (e.g., serialization errors, unknown network issues)
	            log.error("Unexpected error sharing media files {}: {}", request.getMediaIds(), ex.getMessage(), ex);
	        }
	    }
	    
	    return false;
	}
	
	public Mono<Void> processRetraction(String retractId, String toUserKey, String fromUserKey, XmppPrincipal principal) {
	    return offlineMessageService.findByMessageIdAndSender(UUID.fromString(retractId), UUID.fromString(fromUserKey))
	            .flatMap(message -> {
	                log.info("Message found, soft deleting offline record: {}", retractId);
	                String newString = "<body>This message was deleted</body>";
	                message.setStanzaXml(XmppStanzaUtil.markAsRetractedStanza(message.getStanzaXml(), newString));
	                message.setCountable(false);
	                
	                // Chain the decrement smoothly into the save flow instead of calling .subscribe()
	                return unreadCountService.decrementUnreadCount(toUserKey, fromUserKey, UUID.fromString(retractId), principal)
	                        .then(offlineMessageService.save(message))
	                        .then();
	            });
	}

	private Mono<Void> pushNotification(ChannelHandlerContext ctx,
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
					&& xml.contains(Constants.NS_JINGLE)) {   

				// Handle Jingle Signaling notification
				return jingleNotificationHandler.handlePush(ctx, id, toUserKey, fromUserKey, xml, principal);

			} else {                 
				/*
				 * Standard Message Handling
				 * If the stanza is not a call initiation, treat it as a standard chat message.
				 * We extract the <body> element and trigger a Push Notification (FCM/APNs)
				 * to the recipient, ensuring they receive the message even if offline.
				 */            	
				if (XmppStanzaUtil.isArchivable(xml)) {
					String body = XmppUtil.getMessageBody(xml);
					return sendPushNotification(toUserKey, body, NotificationType.DIRECT_MESSAGE, principal);
				}
			}     
		}
		return Mono.empty();
	}  
		
	/**
	 * Used to send push notification for new message
	 *
	 * @param toKey
	 * @param message
	 * @param notifcationType
	 */
	private Mono<Void> sendPushNotification(String toKey, String message, NotificationType notificationType, XmppPrincipal principal) {
	    return Mono.fromRunnable(() -> {
	        // Explicitly set the tenant context for this synchronous boundary worker thread
	        TenantContext.setCurrentTenant(principal.getTenantId());
	        try {
	            Notification notif = Notification.builder()
	                    .receiverIds(Set.of(toKey))
	                    .type(notificationType)
	                    .title("You have new message")
	                    .body(message)
	                    .tenantId(principal.getTenantId())
	                    .build();

	            notificationService.sendPush(notif);
	        } finally {
	            // Clean up the ThreadLocal to prevent leakage back into the worker pool
	            TenantContext.clear();
	        }
	    })
	    .subscribeOn(Schedulers.boundedElastic()) // Offload the network/IO push operation completely
	    .doOnError(e -> log.error("Failed to deliver reactive push notification to user key: {}", toKey, e))
	    .then(); // Transforms Mono<Object> into a clean Mono<Void> pipeline signal
	}
}
