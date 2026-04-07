package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.parser.GroupChatParser;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.XmppStanzaMucUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppStreamManagementUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core handler for Multi-User Chat (MUC) routing and logic.
 * This component manages the lifecycle of group chat stanzas, including archiving (MAM),
 * JID rewriting for anonymity, administrative commands, and push notification triggers.
 * * Complies with XEP-0045 (MUC) and XEP-0313 (MAM) concepts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppMucHandler {
	private final XmppArchiveService xmppArchiveService;
	private final GroupClient groupClient;
	private final UserSessionRegistry userSessionRegistry;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final NotificationService notificationService;
	private final MucCommandDispatcher mucAdminHandler;
	private final DomainProperties domainProperties;
    
	/**
	 * Main entry point for MUC stanza routing.
	 * * @param ctx The Netty channel context for the sender.
	 * @param id The unique stanza ID.
	 * @param roomJid The JID of the target MUC room.
	 * @param fromJid The real JID of the sender.
	 * @param type The XMPP stanza type (e.g., groupchat, set, get).
	 * @param originalXml The raw XML payload.
	 * @param groupChatDomain The domain used for MUC (e.g., conference.example.com).
	 */
	public void handleGroupChatRouting(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, String type, String originalXml) {
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
        XmppMessageType msgType = XmppMessageType.fromString(type);
        
		String roomId = XmppUtil.getRoomId(roomJid);
		String updatedXml = originalXml;

		// Performance Optimization: Only scan the first 500 characters for routing metadata
		String xmlHeader = originalXml.substring(0, Math.min(originalXml.length(), 500));

		// 1. ARCHIVING & STREAM MANAGEMENT (XEP-0198 / XEP-0313)
		if(msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) {
			StanzaInfo info = GroupChatParser.parse(originalXml);
			Ulid ulid = UlidCreator.getMonotonicUlid();
			String ulidString = ulid.toLowerCase();

			// Inject XEP-0359 Stanza IDs for message synchronization
			String stanzaIdExtension = String.format(
					"<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
					principal.getDomain(), 
					ulidString
					);
			updatedXml = originalXml.replace("</message>", stanzaIdExtension + "</message>");

			xmppArchiveService.archiveEvent(updatedXml, info, roomId, fromJid, ulidString)
			.doOnSuccess(saved -> {
				// XEP-0198: Increment the inbound handled count and send 'h' ack to the sender
				XmppStreamManagementUtil.incrementAndSendInboundH(ctx);
				
				log.debug("Event archived [{}]: category={}", id, info.getCategory());
			})
			.doOnError(e -> {
				log.error("Storage failure for message {}: {}", id, e.getMessage());
				XmppUtil.sendError(ctx, id, roomJid, fromJid, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
			})
			.subscribe();
		} else {
			// XEP-0198: Increment the inbound handled count and send 'h' ack to the sender
			XmppStreamManagementUtil.incrementAndSendInboundH(ctx);
		}

		try {
			// Fetch room metadata and membership from the internal Group Service
			MucRoomDto group = groupClient.getGroupById(Long.parseLong(roomId));

			// Verify the sender is actually a member of the room
			Optional<MucMember> senderMucMember = group.getMembers().stream()
					.filter(m -> m.getUserKey().equals(principal.getUserKey())).findFirst();

			if(senderMucMember.isEmpty()) {
				log.error("User {} is not a member of group {}", principal.getUserKey(), roomId);
				return;
			}

			// 2. DISPATCHING BY NAMESPACE
			if (XmppMessageType.SET == XmppMessageType.fromString(type) 
					&& originalXml.contains("urn:xmpp:jingle:1")) {
				// Placeholder for Jingle (WebRTC) signaling in MUC
			} else  if (XmppMessageType.SET == XmppMessageType.fromString(type) 
					&& xmlHeader.contains("http://jabber.org/protocol/muc#admin")) {
				// Handle Moderation (Kick, Ban, Mute)
				mucAdminHandler.handleAdminStanza(ctx, roomJid, principal.getBareJid(), originalXml, group, senderMucMember.get());
			} else {
				// Standard message forwarding logic				
				handleReq(ctx, id, roomJid, fromJid, group, senderMucMember.get(),
						xmlHeader, originalXml, updatedXml);
			}
		} catch (NumberFormatException e) {
			log.error("Invalid roomId format: {}", roomId);
		}
	}

	/**
	 * Iterates through room members and performs JID rewriting for delivery.
	 * Ensures that the 'from' JID is the Occupant JID (Room Nickname) and not the real User JID.
	 */
	private void handleReq(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, MucRoomDto group, 
			MucMember senderMucMember, String xmlHeader, String originalXml, String updatedXml) {
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();

		for(MucMember receiverMucMember : group.getMembers()) {
			String toUserKey = receiverMucMember.getUserKey();

			// Determine recipient connectivity state
			Set<UserSession> userSessions = userSessionRegistry.getSessions(toUserKey);
			boolean hasSessions = !CollectionUtils.isEmpty(userSessions);
			boolean hasActiveSession = hasSessions && userSessions.stream()
					.anyMatch(s -> UserState.ACTIVE == s.getState());

			/*
			 * JID REWRITING:
			 * 1. Change 'from' from UserJID to OccupantJID (Room anonymity).
			 * 2. Change 'to' from RoomJID to the specific Recipient's JID for routing.
			 */
			String finalForwardXml = XmppStanzaMucUtil.rewriteMucStanzaForRecipient(originalXml, roomJid, fromJid, 
					toUserKey, domainProperties.getDomain(), senderMucMember);

			if (hasSessions) {
				// Live Delivery: Publish to the cluster for real-time delivery to active sessions
				clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, finalForwardXml);
			}

			// 3. PUSH NOTIFICATIONS
			// Trigger only if the user has no active foreground session and the message is "archiveable" (e.g. has a body)
			if (!hasActiveSession) {
				if (XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) {
					String body = XmppUtil.getMessageBody(originalXml);
					sendPushNotification(toUserKey, body, NotificationType.GROUP_MESSAGE, principal);
				}
			}
		}
	}

	/**
	 * Dispatches a push notification via the internal Notification Service.
	 *
	 * @param toKey The target user's unique identifier.
	 * @param message The plain text body of the message.
	 * @param notificationType The type of notification (e.g., GROUP_MESSAGE).
	 * @param principal The session principal of the sender.
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