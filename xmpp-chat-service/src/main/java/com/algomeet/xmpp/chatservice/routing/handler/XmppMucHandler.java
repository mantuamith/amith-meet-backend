package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.GroupChatParser;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSession;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class XmppMucHandler {
	private final XmppArchiveService xmppArchiveService;
	private final GroupClient groupClient;
	private final UserSessionRegistry userSessionRegistry;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final NotificationService notificationService;

	/**
	 * Handles routing for Multi-User Chat rooms.
	 * Strategy: Save every event (Message, Reaction, Retraction) and forward to client.
	 */
	public void handleGroupChatRouting(ChannelHandlerContext ctx, String id, String roomJid, String from, String originalXml) {
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		
		String roomId = XmppUtil.getRoomId(roomJid);
		String updatedXml = originalXml;
		
		if(XmppStanzaUtil.isArchiveable(originalXml)) {
			
			// Parse the XML into StanzaInfo (Detects stanzaType, category, targetId)
			StanzaInfo info = GroupChatParser.parse(originalXml);
			
			// Generate the Monotonic ULID
			Ulid ulid = UlidCreator.getMonotonicUlid();
			String ulidString = ulid.toLowerCase();

			// Append to the XML (Using a String builder or XML library)
			// Standard XEP-0359 format: 
			// <stanza-id xmlns='urn:xmpp:sid:0' by='muc.algomeet.com' id='01hgt83x...' />
			String stanzaIdExtension = String.format(
					"<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
					principal.getDomain(), // Your service JID
					ulidString
					);

			// Inject into the original XML before the closing </message> tag
			updatedXml = originalXml.replace("</message>", stanzaIdExtension + "</message>");

			// Process Archiving for all other events (message, reaction, retraction)
			// Pass the internal ID (MAM ID) and parsed info to the service
			xmppArchiveService.archiveEvent(updatedXml, info, roomId, from, ulidString)
			.doOnSuccess(saved -> log.debug("Event archived [{}]: category={}", id, info.getCategory()))
			.doOnError(e -> log.error("Archive failed: {}", e.getMessage()))
			.subscribe();
		}

		// Fetch Group Metadata & Handle Live Routing
		try {
			// Get room members
			MucRoomDto group = groupClient.getGroupById(Long.parseLong(roomId));
			Optional<MucMember> senderMucMember = group.getMembers().stream()
					.filter(m -> m.getUserKey().equals(principal.getUserKey())).findFirst();
			
			if(senderMucMember.isEmpty()) {
				log.error("User is not a member of the group with user ID {}", principal.getUserKey());
				
			}
					
			for(MucMember recieverMucMember : group.getMembers()) {
				String to = recieverMucMember.getUserKey();
				
				if (to.equals(senderMucMember.get())) {
					continue;
				}

				// Retrieve user session status
				Set<UserSession> userSessions = userSessionRegistry.getSessions(to);

				boolean hasSessions = !CollectionUtils.isEmpty(userSessions);
				boolean hasActiveSession = hasSessions && userSessions.stream()
						.anyMatch(s -> UserState.ACTIVE == s.getState());

				if (hasSessions) {
					/*
					 * Jingle Signaling Detection (XEP-0166)
					 * We use a "Fast-Scan" approach using indexOf() to minimize CPU cycles.
					 * 
					 * - 'urn:xmpp:jingle:1': Ensures the stanza belongs to the Jingle namespace.
					 */
					if (originalXml.contains("urn:xmpp:jingle:1") && originalXml.contains("to=")) {
						// TODO: Routes MUC Jingle 

					} else {
						// Broadast to Redis: Even if they are AWAY/DND, we attempt delivery 
						// to their active WebSocket channels across the cluster.
						clusterMessagePublisher.convertAndSendToUser(id, to, from, updatedXml);
					}
				}

				// Push Notification Logic
				// Trigger push if the user has no sessions OR no session is currently 'ACTIVE'
				if (!hasActiveSession) {
					log.debug("User {} has no active sessions. Triggering push notification.", to);

					/*
					 * Jingle Signaling Detection (XEP-0166)
					 * We use a "Fast-Scan" approach using indexOf() to minimize CPU cycles.
					 * 
					 * - 'urn:xmpp:jingle:1': Ensures the stanza belongs to the Jingle namespace.
					 */
					if (originalXml.indexOf("urn:xmpp:jingle:1") != -1) {
						// TODO: create notification for MUC Jingle

					} else { 

						/*
						 * Standard Message Handling
						 * If the stanza is not a call initiation, treat it as a standard chat message.
						 * We extract the <body> element and trigger a Push Notification (FCM/APNs)
						 * to the recipient, ensuring they receive the message even if offline.
						 */
						if (XmppStanzaUtil.isPushEligible(originalXml)) {
							String body = XmppUtil.getMessageBody(originalXml);
							sendPushNotification(to, body, NotificationType.GROUP_MESSAGE, principal);
						}
					}     
				}
			}

		} catch (NumberFormatException e) {
			log.error("Invalid roomId format: {}", roomId);
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