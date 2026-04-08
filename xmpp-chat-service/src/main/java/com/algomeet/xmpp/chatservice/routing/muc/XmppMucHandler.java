package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;
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
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.parser.GroupChatParser;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.call.CallLifeCycleTracker;
import com.algomeet.xmpp.chatservice.routing.call.JingleNotificationHandler;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.JidUtil;
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
	private final GroupCacheService groupCacheService;
	private final UserSessionRegistry userSessionRegistry;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final NotificationService notificationService;
	private final MucAdminCommandDispatcher mucAdminCmdHandler;
	private final DomainProperties domainProperties;
	private final MucUserCommandDispatcher mucUserCommandDispatcher;
	private final JingleNotificationHandler jingleNotificationHandler;
	private final CallLifeCycleTracker callTracker;
    private final JidUtil jidUtil;
    
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

		// Fetch room metadata and membership from the either cache
		MucRoomDto group = groupCacheService.getCachedGroup(Long.parseLong(roomId));

		// Verify if the sender is actually a member of the room/ group
		Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey())).findFirst();

		if(senderMucMember.isEmpty()
				|| senderMucMember.get().isMuted()) {
			log.error("User {} is not a member of group {} or user is muted", principal.getUserKey(), roomId);
			
			XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.INTERNAL_SERVER_ERROR, "You are not allowed to send messages to this room");
			return;
		}
		
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
				if (e instanceof DuplicateKeyException) {
					XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
							XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key");
				} else {
					XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.WAIT, 
							XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
				}
			})
			.subscribe();
		} else {
			// XEP-0198: Increment the inbound handled count and send 'h' ack to the sender
			XmppStreamManagementUtil.incrementAndSendInboundH(ctx);
		}

		try {			
			if (XmppMessageType.SET == XmppMessageType.fromString(type) 
					&& xmlHeader.contains("http://jabber.org/protocol/muc#admin")) {
				// Handle Moderation (Kick, Ban, Mute)
				mucAdminCmdHandler.handleCommandStanza(ctx, roomJid, principal.getBareJid(), originalXml, senderMucMember.get());
			} else if(isUserCommandStanza(originalXml, roomJid)) {
				// Handle user command
				mucUserCommandDispatcher.handleCommandStanza(ctx, roomJid, principal.getBareJid(), originalXml, principal);		
				
			} else {
				// Standard message forwarding logic				
				handleReq(ctx, id, roomJid, fromJid, msgType, group, senderMucMember.get(),
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
	private void handleReq(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, XmppMessageType msgType, MucRoomDto group, 
			MucMember senderMucMember, String xmlHeader, String originalXml, String updatedXml) {

		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		boolean isJingleStanza = XmppStanzaUtil.isJingleStanza(msgType, updatedXml);
		boolean isJingleSessionInitiate = isJingleStanza && originalXml.contains("session-initiate");
		
		// Check for response Jingle
		if (isJingleStanza && !(isJingleSessionInitiate)) {		
			// Process response Jingle
			// Verify if the sender is actually a member of the room/ group
			Optional<MucMember> recieverMucMember = group.getMembers().stream()
					.filter(m -> m.getNickname() != null && m.getNickname().equalsIgnoreCase(jidUtil.getNickname(roomJid)))
					.findFirst();
			
			if (recieverMucMember.isEmpty()) {
				XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
						XmppErrorConditions.BAD_REQUEST, "Receiver not found");
				return;
			}
			
			// Get receiver user key
			String toUserKey = recieverMucMember.get().getUserKey();
			
			// Handle call life cycle   	
			callTracker.track(ctx, jidUtil.getBareJid(toUserKey), fromJid, originalXml, principal, XmppUtil.getRoomId(roomJid));
			
			/*
			 * JID REWRITING:
			 * 1. Change 'from' from UserJID to OccupantJID (Room anonymity).
			 * 2. Change 'to' from RoomJID to the specific Recipient's JID for routing.
			 */
			String finalForwardXml = XmppStanzaMucUtil.rewriteMucStanzaForRecipient(originalXml, roomJid, fromJid, 
					toUserKey, domainProperties.getDomain(), senderMucMember);
			
			// Live Delivery: Publish to the cluster for real-time delivery to active sessions
			clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, finalForwardXml);

		} else {
			for(MucMember receiverMucMember : group.getMembers()) {
				String toUserKey = receiverMucMember.getUserKey();

				// Handle call life cycle 
				if (isJingleStanza) {	        	
					callTracker.track(ctx, jidUtil.getBareJid(toUserKey), fromJid, originalXml, principal, XmppUtil.getRoomId(roomJid));
				}

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
					if ((msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) 
							|| isJingleSessionInitiate) {

						if (isJingleSessionInitiate) {
							// Handle Jingle Signaling notification
							jingleNotificationHandler.handlePush(ctx, id, toUserKey, XmppUtil.getUserKey(fromJid), originalXml, principal);
						} else {
							String body = XmppUtil.getMessageBody(originalXml);
							sendPushNotification(toUserKey, body, NotificationType.GROUP_MESSAGE, principal);
						}
					}
				}
			}
		}
	}
	
	/**
     * Determines if the incoming XML stanza is a user-initiated command.
     * <p>
     * In XMPP MUC, a presence stanza directed at a room with a specific nickname 
     * (e.g., room@conference.domain/newNick) indicates a command like a nickname change, 
     * rather than a simple room join or status update.
     * </p>
     * * @param xml     The raw XML payload to check.
     * @param roomJid The target JID of the stanza.
     * @return {@code true} if the stanza is a presence-based command; {@code false} otherwise.
     */
    private boolean isUserCommandStanza(String xml, String roomJid) {
        // 1. First, verify the stanza type is actually a <presence/>
        if (XmppStanzaUtil.isPresenceStanza(xml)) {
            
            // 2. Split the JID to check for the presence of a Resource (the nickname)
            // Example: "dev-team@muc.algomeet.com/Jack" -> ["dev-team@muc.algomeet.com", "Jack"]
            String[] jidArr = roomJid.split("/");
            
            // 3. If a nickname is provided in the JID (length > 1), it indicates 
            // a specific targeted action/command within the MUC context.
            if (jidArr.length > 1 && StringUtils.hasText(jidArr[1])) {
                return true;
            }
        }
        
        // Default to false if it's not a presence or is missing a specific nickname resource
        return false;
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