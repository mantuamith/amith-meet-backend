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
import com.algomeet.xmpp.chatservice.routing.call.JingleNotificationHandler;
import com.algomeet.xmpp.chatservice.routing.call.MucCallLifeCycleTracker;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.service.MucUnreadCountService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.XmppServerAckSender;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaMucUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * <h2>XmppMucHandler</h2>
 * Core coordinator for Multi-User Chat (MUC) logic, routing, and persistence.
 * * <p>This component serves as the central hub for room-based communication. It handles:
 * <ul>
 * <li><b>Authorization:</b> Verifying sender membership and mute status.</li>
 * <li><b>Persistence (MAM):</b> Archiving messages according to XEP-0313.</li>
 * <li><b>Anonymization:</b> Rewriting JIDs to protect user privacy (Occupant JIDs).</li>
 * <li><b>Real-time Routing:</b> Local and Cluster-wide delivery via Netty and Redis.</li>
 * <li><li><b>Push Notifications:</b> Notifying offline or backgrounded users.</li>
 * </ul>
 * * @see <a href="https://xmpp.org/extensions/xep-0045.html">XEP-0045: Multi-User Chat</a>
 * @see <a href="https://xmpp.org/extensions/xep-0313.html">XEP-0313: Message Archive Management</a>
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
	private final MucCallLifeCycleTracker mucCallTracker;
	private final JidUtil jidUtil;
	private final MucUnreadCountService mucUnreadCountService;

	/**
	 * Main entry point for MUC stanza processing.
	 * Decides whether a stanza is a moderation command, a user command, or a standard message.
	 *
	 * @param ctx         The Netty channel context for the current TCP connection.
	 * @param id          The 'id' attribute of the XMPP stanza.
	 * @param toRoomJid   The destination JID (e.g., room@conference.domain/nickname).
	 * @param fromJid     The real JID of the sender.
	 * @param type        The message type (e.g., groupchat, error, presence).
	 * @param originalXml The full raw XML payload.
	 */
	public void handleGroupChatRouting(ChannelHandlerContext ctx, String id, String toRoomJid, String fromJid, String type, String originalXml) {
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		XmppMessageType msgType = XmppMessageType.fromString(type);

		String toRoomId = XmppUtil.getRoomId(toRoomJid);
		String forArchiveXml = originalXml;

		// 1. AUTHORIZATION & ROOM LOOKUP
		// Fetch room metadata and membership from the cache
		MucRoomDto group = groupCacheService.getCachedGroup(toRoomId);

		// Verify if the sender is an authorized member and is not muted
		Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey())).findFirst();

		if(senderMucMember.isEmpty() || senderMucMember.get().isMuted()) {
			log.error("Access Denied: User {} in room {}. (Member: {}, Muted: {})", 
					principal.getUserKey(), toRoomId, senderMucMember.isPresent(), senderMucMember.map(MucMember::isMuted).orElse(false));

			XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.INTERNAL_SERVER_ERROR, "You are not allowed to send messages to this room");
			return;
		}

		// 2. DIRECT PRIVATE MESSAGE (PM) WITHIN MUC CHECK
		MucMember directPmRecipientMucMember = resolveDirectPmRecipient(ctx, id, fromJid, toRoomJid, group);

		// Optimization: Peek at headers for routing decisions
		String xmlHeader = originalXml.substring(0, Math.min(originalXml.length(), 500));
		
		// 3. ARCHIVING (MAM - XEP-0313)
		// Only archive messages that are storage-eligible (e.g., contain a <body>)
		if(msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) {
			StanzaInfo info = GroupChatParser.parse(originalXml);
			String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();

			// Inject Stanza-ID (XEP-0359) to facilitate client-side de-duplication and synchronization
			String stanzaIdExtension = String.format("<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
					principal.getDomain(), ulidString);
			forArchiveXml = originalXml.replace("</message>", stanzaIdExtension + "</message>");

			xmppArchiveService.archiveEvent(forArchiveXml, info, toRoomJid, (directPmRecipientMucMember != null ? directPmRecipientMucMember.getUserKey() : null), 
					fromJid, ulidString)
			.doOnSuccess(saved -> {
				// XEP-0198 Stream Management: Acknowledge reception to sender
				log.debug("MAM Archive Success: ID={} Room={}", ulidString, toRoomId);
				
				if (directPmRecipientMucMember != null) {
					// Increment MUC unread messages count 
					mucUnreadCountService.incrementUnreadCount(directPmRecipientMucMember.getUserKey(), 
							Long.parseLong(XmppUtil.getRoomId(toRoomJid)))
					.doOnError(e -> {
						log.error("Storage failure for increment muc messages count {}: {}", id, e.getMessage(), e);
					})
					.subscribe();
				} else {
					// Increment MUC unread messages count 
					mucUnreadCountService.incrementForRoomMembers(Long.parseLong(XmppUtil.getRoomId(toRoomJid)),
							group.getMembers().stream().map(m -> m.getUserKey()).toList(), 
							senderMucMember.get().getUserKey())
					.doOnError(e -> {
						log.error("Storage failure for increment muc messages count {}: {}", id, e.getMessage(), e);
					})
					.subscribe();
				}
				
				// Server ACK
				XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), fromJid);
			})
			.doOnError(e -> {
				log.error("MAM Archive Failure: {}", e.getMessage(), e);
				handleArchiveError(ctx, id, fromJid, e);
			})
			.subscribe();
		} else {
			
			// Server ACK
			XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), fromJid);		
		}

		// 4. DISPATCHING
		try {			
			if (isModerationCommand(type, xmlHeader)) {
				// MUC Admin actions (kick, ban, mute)
				mucAdminCmdHandler.handleCommandStanza(ctx, toRoomJid, principal.getBareJid(), originalXml, senderMucMember.get());
			} else if(isUserCommandStanza(originalXml, toRoomJid)) {
				// MUC User actions (nickname changes, room entry)
				mucUserCommandDispatcher.handleCommandStanza(ctx, toRoomJid, principal.getBareJid(), originalXml, principal);		
			} else {
				// Standard message propagation to members
				handleReq(ctx, id, toRoomJid, fromJid, msgType, group, senderMucMember.get(), directPmRecipientMucMember, xmlHeader, originalXml);
			}
		} catch (NumberFormatException e) {
			log.error("Critical: Invalid roomId format in routing: {}", toRoomId);
		}
	}

	/**
	 * Handles distribution logic. Iterates through members or targets a specific occupant 
	 * for Private Messages.
	 */
	private void handleReq(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, XmppMessageType msgType, MucRoomDto group, 
			MucMember senderMucMember, MucMember directReceiverMucMember, String xmlHeader, String originalXml) {

		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		boolean isJingleStanza = XmppStanzaUtil.isJingleStanza(msgType, xmlHeader);
		boolean isJingleSessionInitiate = isJingleStanza && originalXml.contains("session-initiate");

		if (directReceiverMucMember != null) {			
			// Target: Single recipient (Private Message within MUC)
			publishOrNotify(ctx, id, roomJid, fromJid, msgType, senderMucMember, xmlHeader, originalXml, 
					directReceiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal);
		} else {						
			// Target: All room members (Broadcast)
			for(MucMember receiverMucMember : group.getMembers()) {
				publishOrNotify(ctx, id, roomJid, fromJid, msgType, senderMucMember, xmlHeader, originalXml, 
						receiverMucMember.getUserKey(), isJingleStanza, isJingleSessionInitiate, principal);
			}
		}
	}

	/**
	 * Core delivery method. Performs JID rewriting, cluster publishing, and push notification triggering.
	 */
	private void publishOrNotify(ChannelHandlerContext ctx, String id, String roomJid, String fromJid, XmppMessageType msgType, 
			MucMember senderMucMember, String xmlHeader, String originalXml, String toUserKey, boolean isJingleStanza,
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
		
		if (hasSessions) {
			clusterMessagePublisher.convertAndSendToUser(id, toUserKey, principal.getUserKey(), ChatType.GROUPCHAT, finalForwardXml);
		}

		// 4. Push Notifications (Offline Storage logic)
		boolean hasActiveSession = hasSessions && userSessions.stream().anyMatch(s -> UserState.ACTIVE == s.getState());

		if (!hasActiveSession) {					
			if ((msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(xmlHeader, originalXml)) || isJingleSessionInitiate) {
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
	 * Resolves a MUC occupant for Private Messaging (PM).
	 * Returns the member if found, or null if the message is a standard group broadcast.
	 * If a nickname was provided but the user isn't found, it handles the error response and throws an exception.
	 */
	private MucMember resolveDirectPmRecipient(ChannelHandlerContext ctx, String id, String fromJid, String toRoomJid, MucRoomDto group) {
	    String nickname = jidUtil.getNickname(toRoomJid);
	    
	    // If no nickname is present, this is a standard group message, not a PM.
	    if (!StringUtils.hasText(nickname)) {
	        return null;
	    }

	    return group.getMembers().stream()
	            .filter(m -> nickname.equalsIgnoreCase(m.getUserKey()))
	            .findFirst()
	            .orElseGet(() -> {
	                log.error("PM Failure: Nickname {} not found in room {}", nickname, toRoomJid);
	                XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), 
	                        XmppErrorType.CANCEL, XmppErrorConditions.BAD_REQUEST, 
	                        "Receiver is not member of the group/room.");
	                
	                // Throwing a custom exception or a runtime exception stops the execution 
	                // of the parent method effectively, replacing the 'return' statement.
	                throw new RuntimeException("Receiver not found in room: " + nickname);
	            });
	}

	private boolean isModerationCommand(String type, String xmlHeader) {
		return XmppMessageType.SET == XmppMessageType.fromString(type) 
				&& xmlHeader.contains("http://jabber.org/protocol/muc#admin");
	}

	private void handleArchiveError(ChannelHandlerContext ctx, String id, String fromJid, Throwable e) {
		if (e instanceof DuplicateKeyException) {
			// Silent error to handle retry
			/*
			XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key"); */
		} else {
			XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.WAIT, 
					XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
		}
	}

	/**
	 * Determines if the incoming XML stanza is a user-initiated command (e.g. Presence Nickname change).
	 *
	 * @param xml     The raw XML payload.
	 * @param roomJid The target JID.
	 * @return {@code true} if targeting a specific occupant resource via presence.
	 */
	private boolean isUserCommandStanza(String xml, String roomJid) {
		if (XmppStanzaUtil.isPresenceStanza(xml)) {
			String[] jidArr = roomJid.split("/");
			return jidArr.length > 1 && StringUtils.hasText(jidArr[1]);
		}
		return false;
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