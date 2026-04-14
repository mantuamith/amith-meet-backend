package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.parser.GroupChatParser;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.service.MucUnreadCountService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppReadUtil;
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
	private final MucAdminCommandDispatcher mucAdminCmdHandler;
	private final DomainProperties domainProperties;
	private final MucUserCommandDispatcher mucUserCommandDispatcher;
	private final XmppBroadCastHandler xmppBroadCastHandler;
	private final JidUtil jidUtil;
	private final MucUnreadCountService mucUnreadCountService;
	private final XmppReadUtil xmppReadUtil;

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

				// --- XEP-0333: Chat Markers (Read Receipts) ---
				// If the stanza contains the 'urn:xmpp:chat-markers:0' namespace (displayed), 
				// the user has actively viewed the conversation.
				if (xmlHeader.contains(XmppReadUtil.NS_DISPLAYS)) {
					isAckMessage = true;
					String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);

					if (StringUtils.hasText(ackMessageId)) {
						// Decrement the unread counter for this specific sender-recipient pair.
						// Note: fromUserKey is the person who read it, toUserKey is the original sender.
						mucUnreadCountService.decrementUnreadCount(senderMucMember.get().getUserKey(), toRoomId).subscribe();
					}
				}


				if (directPmRecipientMucMember != null) {
					if(!isAckMessage) {
						// Increment MUC unread messages count 
						mucUnreadCountService.incrementUnreadCount(directPmRecipientMucMember.getUserKey(), 
								XmppUtil.getRoomId(toRoomJid))
						.doOnError(e -> {
							log.error("Storage failure for increment muc messages count {}: {}", id, e.getMessage(), e);
						})
						.subscribe();
					}
				} else {
					if(!isAckMessage) {
						// Increment MUC unread messages count 
						mucUnreadCountService.incrementForRoomMembers(XmppUtil.getRoomId(toRoomJid),
								group.getMembers().stream().map(m -> m.getUserKey()).toList(), 
								senderMucMember.get().getUserKey())
						.doOnError(e -> {
							log.error("Storage failure for increment muc messages count {}: {}", id, e.getMessage(), e);
						})
						.subscribe();
					}
				}	

				log.debug("MAM Archive Success: ID={} Room={}", ulidString, toRoomId);
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
				xmppBroadCastHandler.broadcastToOccupants(ctx, id, toRoomJid, fromJid, msgType, group, senderMucMember.get(), 
						directPmRecipientMucMember, xmlHeader, originalXml);
			}
		} catch (NumberFormatException e) {
			log.error("Critical: Invalid roomId format in routing: {}", toRoomId);
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
			// Duplicate stanza detected (idempotent case).
			// Client MUST ignore this error; used only to support safe retries.
			XmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key");

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
}