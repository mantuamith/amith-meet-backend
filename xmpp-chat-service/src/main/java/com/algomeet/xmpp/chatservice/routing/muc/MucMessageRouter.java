package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;
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
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaMucUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MucMessageRouter {
	private final UserSessionRegistry userSessionRegistry;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final NotificationService notificationService;
	private final DomainProperties domainProperties;
	private final JingleNotificationHandler jingleNotificationHandler;
	private final MucCallLifeCycleTracker mucCallTracker;
	private final JidUtil jidUtil;


	/**
	 * Handles distribution logic. Iterates through members or targets a specific occupant 
	 * for Private Messages.
	 */
	public void broadcastToOccupants(ChannelHandlerContext ctx, String id, String toRoomJid, String fromJid, XmppMessageType msgType, Group group, 
			GroupMember directReceiverMucMember, String originalXml) {

		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		boolean isJingleStanza = XmppStanzaUtil.isJingleStanza(msgType, originalXml);
		boolean isJingleSessionInitiate = isJingleStanza && originalXml.contains("session-initiate");

		if (directReceiverMucMember != null) {			
			// Target: Single recipient (Private Message within MUC)
			publishOrNotify(ctx, id, toRoomJid, fromJid, msgType, originalXml, 
					directReceiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal, group.getMessageRetentionDays());
		} else {						
			// Target: All room members (Broadcast)
			for(GroupMember receiverMucMember : group.getMembers()) {
				publishOrNotify(ctx, id, toRoomJid, fromJid, msgType, originalXml, 
						receiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal, group.getMessageRetentionDays());
			}
		}
	}

	/**
	 * Core delivery method. Performs JID rewriting, cluster publishing, and push notification triggering.
	 */
	private void publishOrNotify(ChannelHandlerContext ctx, String id, String toRoomJid, String fromJid, XmppMessageType msgType, 
			String originalXml, String toUserKey, boolean isJingleStanza,
			boolean isJingleSessionInitiate, XmppPrincipal principal, Integer messageRetentionDays) {

		// 1. Call Tracking (For VoIP/Video logic)
		if (isJingleStanza) {	        	
			mucCallTracker.track(ctx, jidUtil.getBareJid(toUserKey), fromJid, originalXml, principal, 
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
		Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
		boolean hasSessions = !CollectionUtils.isEmpty(userSessions);

		// 4. Publish to cluster server
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, false, finalForwardXml, principal);

		// 5. Push Notifications (Offline Storage logic)
		boolean hasActiveSession = hasSessions && userSessions.stream().anyMatch(s -> UserState.ACTIVE == s.getState());

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
			        jingleNotificationHandler.handlePush(
			                ctx,
			                id,
			                toUserKey,
			                XmppUtil.getUserKey(fromJid),
			                originalXml,
			                principal
			        );
			    } else {
			        String body = XmppUtil.getMessageBody(originalXml);
			        sendPushNotification(toUserKey, body, NotificationType.GROUP_MESSAGE, principal);
			    }
			}
		}
	}
	
	/**
	 * Iterates through all room occupants and publishes the presence update to the cluster.
	 */
	public void broadcastToOccupants(String id, String senderKey, Group group, String payload, boolean isAllowEcho) {
		if(group == null || group.getMembers() == null) {
			return;
		}
		
		for(GroupMember receiver : group.getMembers()) {
			if (!isAllowEcho) {
				if (receiver.getUserKey().equalsIgnoreCase(senderKey)) {
					continue;
				}
			}
			
			clusterMessagePublisher.convertAndSendToUser(id, receiver.getUserKey(), senderKey, ChatType.GROUPCHAT, payload);
		}
	}
	
	/**
	 * Iterates through all room occupants and publishes the presence update to the cluster except don't echo to same user session ID as sender.
	 */
	public void broadcastToOccupants(String id, String senderKey, Group group, String payload, String sessionId) {
		if(group == null || group.getMembers() == null) {
			return;
		}
		
		XmppPrincipal principal = XmppPrincipal.builder()
				.sessionId(sessionId)
				.build();
		
		for(GroupMember receiver : group.getMembers()) {			
			clusterMessagePublisher.convertAndSendToUser(id, receiver.getUserKey(), senderKey, ChatType.GROUPCHAT, 
					false, payload, principal);
		}
	}	

	/**
	 * Dispatches a push notification via the internal Notification Service.
	 */
	private void sendPushNotification(String toKey, String message, NotificationType notificationType, XmppPrincipal principal) {
		Notification notif = Notification.builder()
				.receiverIds(Set.of(toKey))
				.type(notificationType)
				.title("You have a new message")
				.body(message)
				.tenantId(principal.getTenantId())
				.build();

		notificationService.sendPush(notif);
	}
}
