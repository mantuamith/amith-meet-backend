package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.call.JingleNotificationHandler;
import com.algomeet.xmpp.chatservice.routing.call.MucCallLifeCycleTracker;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaMucUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class MucMessageRouter {
	private final ClusterMessagePublisher reactiveClusterMessagePublisher;
	private final UserSessionRegistry userSessionRegistry;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final DomainProperties domainProperties;
	private final JingleNotificationHandler jingleNotificationHandler;
	private final MucCallLifeCycleTracker mucCallTracker;
	private final NotificationService notificationService;
	private final JidUtil jidUtil;

	/**
	 * Handles distribution logic. Iterates through members or targets a specific occupant 
	 * for Private Messages.
	 */
	public Mono<Void> broadcastToOccupants(ChannelHandlerContext ctx, String id, String toRoomJid, String fromJid, XmppMessageType msgType, Group group, 
			GroupMember directReceiverMucMember, String originalXml) {

		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		boolean isJingleStanza = XmppStanzaUtil.isJingleStanza(msgType, originalXml);
		boolean isJingleSessionInitiate = isJingleStanza && originalXml.contains("session-initiate");

		if (directReceiverMucMember != null) {			
			// Target: Single recipient (Private Message within MUC)
			return publishOrNotify(ctx, id, toRoomJid, fromJid, msgType, originalXml, 
					directReceiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal, group.getMessageRetentionDays());
		} else {						
			// Target: All room members (Broadcast)
			if (group == null || CollectionUtils.isEmpty(group.getMembers())) {
				return Mono.empty();
			}
			return Flux.fromIterable(group.getMembers())
					.flatMap(receiverMucMember -> publishOrNotify(ctx, id, toRoomJid, fromJid, msgType, originalXml, 
							receiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal, group.getMessageRetentionDays()))
					.then();
		}
	}

	/**
	 * Core delivery method. Performs JID rewriting, cluster publishing, and push notification triggering.
	 */
	private Mono<Void> publishOrNotify(ChannelHandlerContext ctx, String id, String toRoomJid, String fromJid, XmppMessageType msgType, 
			String originalXml, String toUserKey, boolean isJingleStanza,
			boolean isJingleSessionInitiate, XmppPrincipal principal, Integer messageRetentionDays) {

		// 1. Call Tracking (For VoIP/Video logic)
		Mono<Void> trackingMono = Mono.empty();
		if (isJingleStanza) {	        	
			trackingMono = mucCallTracker.track(ctx, jidUtil.getBareJid(toUserKey), fromJid, originalXml, principal, 
					UUID.fromString(XmppUtil.getRoomId(toRoomJid)), messageRetentionDays);
		}

		// 2. Anonymization (JID Rewriting)
		// We MUST hide the real JID of the sender and use the room nickname to comply with MUC anonymity.
		/*
		 * JID REWRITING:
		 * 1. Change 'from' from UserJID to OccupantJID (Room anonymity).
		 * 2. Change 'to' from RoomJID to the specific Recipient's JID for routing.
		 */
		String finalForwardXml = XmppStanzaMucUtil.rewriteMucStanzaForRecipient(originalXml, toRoomJid, fromJid, 
				toUserKey, domainProperties.getDomain());

		// 3. Live Delivery
		return trackingMono.then(userSessionRegistry.getSessions(toUserKey))
				.flatMap(userSessions -> {
					boolean hasSessions = !CollectionUtils.isEmpty(userSessions);

					// 4. Publish to cluster server
					Mono<Void> clusterPublishMono = clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, false, finalForwardXml, principal).then();

					// 5. Push Notifications (Offline Storage logic)
					boolean hasActiveSession = hasSessions && userSessions.stream().anyMatch(s -> UserState.ACTIVE == s.getState());
					Mono<Void> notificationMono = Mono.empty();

					if (!hasActiveSession) {					
						/**
						 * Handle push notification delivery for archivable messages or Jingle session initiation.
						 *
						 * Conditions:
						 * - Message supports offline storage AND is archivable stanza
						 *   OR
						 * - It is a Jingle session initiation request (real-time call/session setup)
						 *
						 * Behavior:
						 * - For Jingle session initiate: trigger Jingle-specific push handling
						 * - For normal messages: extract message body and send standard push notification
						 */
						if ((msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchivable(originalXml)) || isJingleSessionInitiate) {
							if (isJingleSessionInitiate) {
								notificationMono = jingleNotificationHandler.handlePush(
										ctx,
										id,
										toUserKey,
										XmppUtil.getUserKey(fromJid),
										originalXml,
										principal
										).then();
							} else {
								String body = XmppUtil.getMessageBody(originalXml);
								notificationMono = sendPushNotification(toUserKey, body, NotificationType.GROUP_MESSAGE, principal);
							}
						}
					}

					return Mono.when(clusterPublishMono, notificationMono);
				});
	}
	
	/**
	 * Iterates through all room occupants and publishes the presence update to the cluster.
	 */
	public Mono<Void> broadcastToOccupants(String id, String senderKey, Group group, String payload) {
		if(group == null || group.getMembers() == null) {
			return Mono.empty();
		}

		// Convert the list of members into a reactive stream and process concurrently via flatMap
		return Flux.fromIterable(group.getMembers())
				.flatMap(receiver -> {
					log.debug("Routing MUC stanza [{}] from [{}] to subscriber [{}] via cluster bus.", 
							id, senderKey, receiver.getUserKey());

					return clusterMessagePublisher.convertAndSendToUser(id, receiver.getUserKey(), senderKey, ChatType.GROUPCHAT, payload);
				})
				.then(); // Aggregate downstream signaling back into a single Mono<Void> completion token
	}

	/**
	 * Iterates through all room occupants and publishes the presence update to the cluster 
	 * except don't echo to same user session ID as sender.
	 */
	public Mono<Void> broadcastToOccupants(String id, String senderKey, Group group, String payload, String sessionId) {
		// Guard clause to handle missing groups or empty member structures gracefully
		if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
			log.warn("Attempted to broadcast MUC message to an empty or non-existent group. StanzaId: {}", id);
			return Mono.empty();
		}

		XmppPrincipal principal = XmppPrincipal.builder()
				.sessionId(sessionId)
				.build();

		// Convert the list of members into a reactive stream and process concurrently via flatMap
		return Flux.fromIterable(group.getMembers())
				.flatMap(receiver -> {
					log.debug("Routing MUC stanza [{}] from [{}] to subscriber [{}] via cluster bus.", 
							id, senderKey, receiver.getUserKey());

					return reactiveClusterMessagePublisher.convertAndSendToUser(
							id, 
							receiver.getUserKey(), 
							senderKey, 
							ChatType.GROUPCHAT, 
							false, // isAllowEcho = false ensures duplicate suppression via session ID matches
							payload, 
							principal
							);
				})
				.then(); // Aggregate downstream signaling back into a single Mono<Void> completion token
	}

	/**
	 * Dispatches a push notification via the internal Notification Service.
	 */
	private Mono<Void> sendPushNotification(String toKey, String message, NotificationType notificationType, XmppPrincipal principal) {		
		return Mono.fromRunnable(() -> {
	        // Explicitly set the tenant context for this synchronous boundary worker thread
	        TenantContext.setCurrentTenant(principal.getTenantId());
	        try {
	        	Notification notif = Notification.builder()
	    				.receiverIds(Set.of(toKey))
	    				.type(notificationType)
	    				.title("You have a new message")
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