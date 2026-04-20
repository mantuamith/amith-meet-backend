package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
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
	public void broadcastToOccupants(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, XmppMessageType msgType, MucRoomDto group, 
			MucMember senderMucMember, MucMember directReceiverMucMember, String originalXml) {

		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		boolean isJingleStanza = XmppStanzaUtil.isJingleStanza(msgType, originalXml);
		boolean isJingleSessionInitiate = isJingleStanza && originalXml.contains("session-initiate");

		if (directReceiverMucMember != null) {			
			// Target: Single recipient (Private Message within MUC)
			publishOrNotify(ctx, id, roomJid, fromJid, msgType, senderMucMember, originalXml, 
					directReceiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal);
		} else {						
			// Target: All room members (Broadcast)
			for(MucMember receiverMucMember : group.getMembers()) {
				publishOrNotify(ctx, id, roomJid, fromJid, msgType, senderMucMember, originalXml, 
						receiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal);
			}
		}
	}

	/**
	 * Core delivery method. Performs JID rewriting, cluster publishing, and push notification triggering.
	 */
	private void publishOrNotify(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, XmppMessageType msgType, 
			MucMember senderMucMember, String originalXml, String toUserKey, boolean isJingleStanza,
			boolean isJingleSessionInitiate, XmppPrincipal principal) {

		// 1. Call Tracking (For VoIP/Video logic)
		if (isJingleStanza) {	        	
			mucCallTracker.track(ctx, jidUtil.getBareJid(toUserKey), fromJid, originalXml, principal, XmppUtil.getRoomId(roomJid));
		}

		// 2. Anonymization (JID Rewriting)
		// We MUST hide the real JID of the sender and use the room nickname to comply with MUC anonymity.
		/*
		 * JID REWRITING:
		 * 1. Change 'from' from UserJID to OccupantJID (Room anonymity).
		 * 2. Change 'to' from RoomJID to the specific Recipient's JID for routing.
		 */
		String finalForwardXml = XmppStanzaMucUtil.rewriteMucStanzaForRecipient(originalXml, roomJid, fromJid, 
				toUserKey, domainProperties.getDomain(), senderMucMember);

		// 3. Live Delivery
		Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
		boolean hasSessions = !CollectionUtils.isEmpty(userSessions);

		// 4. Publish to cluster server
		clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, finalForwardXml);

		// 5. Push Notifications (Offline Storage logic)
		boolean hasActiveSession = hasSessions && userSessions.stream().anyMatch(s -> UserState.ACTIVE == s.getState());

		if (!hasActiveSession) {					
			if ((msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(originalXml)) || isJingleSessionInitiate) {
				if (isJingleSessionInitiate) {
					jingleNotificationHandler.handlePush(ctx, id, toUserKey, XmppUtil.getUserKey(fromJid), originalXml, principal);
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
	public void broadcastToOccupants(String id, String senderKey, MucRoomDto group, String presence, boolean isAllowEcho) {
		if(group == null || group.getMembers() == null) {
			return;
		}
		
		for(MucMember receiver : group.getMembers()) {
			if (!isAllowEcho) {
				if (receiver.getUserKey().equalsIgnoreCase(senderKey)) {
					continue;
				}
			}
			
			clusterMessagePublisher.convertAndSendToUser(id, receiver.getUserKey(), senderKey, ChatType.GROUPCHAT, presence);
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
