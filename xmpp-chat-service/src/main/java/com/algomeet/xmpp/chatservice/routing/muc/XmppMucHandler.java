package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.PresenceType;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.service.MucMessageReadCursorService;
import com.algomeet.xmpp.chatservice.service.MucMessageService;
import com.algomeet.xmpp.chatservice.service.MucRetractionService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppCustomStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppReadUtil;
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

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
	private final MucAdminCommandRouter mucAdminCommandRouter;
	private final DomainProperties domainProperties;
	private final MucUserCommandRouter mucUserCommandRouter;
	private final MucMessageRouter mucMessageRouter;
	private final JidUtil jidUtil;
	private final XmppReadUtil xmppReadUtil;
	private final XmppUtil xmppUtil;
	private final MucRetractionService mucRetractionService;
	private final MucMessageReadCursorService mucMessageReadService;
	private final MucMessageService mucMessageService;

	/**
	 * Main entry point for MUC stanza processing.
	 * Decides whether a stanza is a moderation command, a user command, or a standard message.
	 *
	 * @param ctx         The Netty channel context for the current TCP connection.
	 * @param id          The 'id' attribute of the XMPP stanza.
	 * @param toRoomJid   The destination JID (e.g., room@conference.domain/<nickname|userkey>).
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
		// Set tenant Id to support multi-tenancy 
		TenantContext.setCurrentTenant(principal.getTenantId());

		MucRoomDto group = groupCacheService.getCachedGroup(toRoomId);

		// Verify if the sender is an authorized member and is not muted
		Optional<MucMember> senderMucMember = group.getMembers().stream()
				.filter(m -> m.getUserKey().equals(principal.getUserKey())).findFirst();


		if((senderMucMember.isEmpty() || senderMucMember.get().isMuted())
				// Ignore unavailable presence stanzas used for member-leave broadcasts
				&& !(XmppStanzaUtil.isPresenceStanza(originalXml) && PresenceType.UNAVAILABLE.getValue().equals(type))) {

			xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.FORBIDDEN, "You are not allowed to send messages to this room");

			log.error("Access Denied: User {} in room {}. (Member: {}, Muted: {})", 
					principal.getUserKey(), toRoomId, senderMucMember.isPresent(), senderMucMember.map(MucMember::isMuted).orElse(false));
			return;
		}

		if (isModerationCommand(type, originalXml)) {
			// MUC Admin actions (kick, ban, mute)
			mucAdminCommandRouter.handleCommandStanza(ctx, toRoomJid, originalXml, senderMucMember.get(), principal);
		} else if(isUserCommandStanza(originalXml, toRoomJid)) {
			// MUC User actions (nickname changes, room entry, member-leave broadcasts)
			mucUserCommandRouter.handleCommandStanza(ctx, type, toRoomJid,  originalXml, principal);	

		} else if(XmppStanzaUtil.isRetractStanza(originalXml)) {
			mucRetractionService.retract(ctx, id, toRoomJid, originalXml, principal);								

		} else {

			// 2. DIRECT PRIVATE MESSAGE (PM) WITHIN MUC CHECK
			MucMember pmToMucMember = resolveDirectPmRecipient(ctx, id, fromJid, toRoomJid, group);

			// 3. ARCHIVING (MAM - XEP-0313)
			// Only archive messages that are storage-eligible (e.g., contain a <body>)
			// Check if it's archivable
			boolean isArchivable = XmppStanzaUtil.isArchivable(originalXml);

			boolean isAckStanza = XmppStanzaUtil.isMessageAckStanza(originalXml);

			/**
			 * Generate a monotonic UUIDv7 used as the stable stanza-id value.
			 *
			 * Why UUIDv7:
			 * - Time-ordered and sortable based on a 48-bit Unix epoch timestamp.
			 * - Highly performant for database primary indexing and chronological message ordering.
			 * - Standard 128-bit structure that stores natively as an optimized 16-byte binary 
			 *   payload (BinData Subtype 4) in MongoDB.
			 * - Acts as an unforgeable server-side sequence identifier for reliable MAM 
			 *   (XEP-0313) history retrieval and RSM pagination cursors.
			 *
			 * Note: Standard UUID text representations are inherently lowercase, ensuring string
			 * consistency if serialized outside of native binary storage layers.
			 */
			UUID stanzaId = UuidCreator.getTimeOrderedEpoch();

			if (isAckStanza) {
				// --- XEP-0333: Chat Markers (Read Receipts) ---
				// If the stanza contains the 'urn:xmpp:chat-markers:0' namespace (displayed), 
				// the user has actively viewed the conversation.
				if (originalXml.contains(XmppReadUtil.NS_DISPLAYS)) {
					String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);
					if (StringUtils.hasText(ackMessageId)) {	
						UUID messageId = UUID.fromString(ackMessageId);
						// Save read MUC message ACK
						mucMessageReadService.advanceReadCursor(UUID.fromString(principal.getUserKey()), group.getId(), messageId)
						.subscribe();
						
						// Read status batch update
						mucMessageService.bulkMarkRoomMessagesAsRead(messageId).subscribe();
					}					
				}
			} else if ((msgType.supportsOfflineStorage() && isArchivable)) {
								
				// Insert stanza ID
				forArchiveXml = XmppStanzaUtil.insertStanzaId(originalXml, stanzaId.toString(), principal.getDomain());		
				Boolean isCountable = XmppCustomStanzaUtil.isCountableMessage(originalXml);
				
				xmppArchiveService.archiveEvent(forArchiveXml, id, XmppUtil.getRoomId(toRoomJid), (pmToMucMember != null ? pmToMucMember.getUserKey() : null), 
						XmppUtil.getUserKey(fromJid), stanzaId, isCountable)
				.doOnSuccess(saved -> {

					// Send an immediate server-level acknowledgment to the sender.
					//
					// This acknowledgment confirms that:
					// 1. The server has successfully received the stanza.
					// 2. The stanza has been persisted to the database.
					// 3. The server has taken full responsibility for further routing/delivery.
					//
					// This is a custom acknowledgment (not client XEP-0198 ack),
					// used to provide early delivery assurance back to the sender.
					XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), stanzaId.toString());
					
					// Move cursor for the message sender
					if (isCountable) {
						mucMessageReadService.advanceReadCursor(UUID.fromString(principal.getUserKey()), group.getId(), UUID.fromString(id))
						.subscribe();
					}

					log.debug("MAM Archive Success: ID={} Room={}", stanzaId, toRoomId);
				})
				.doOnError(e -> {
					log.error("MAM Archive Failure: {}", e.getMessage(), e);
					handleArchiveError(ctx, id, principal, e);
				})
				.subscribe();
			}

			// 4. DISPATCHING
			try {			

				// Standard message propagation to members
				mucMessageRouter.broadcastToOccupants(ctx, id, toRoomJid, fromJid, msgType, group, 
						pmToMucMember, (isArchivable ? forArchiveXml : originalXml));

			} catch (NumberFormatException e) {
				log.error("Critical: Invalid roomId format in routing: {}", toRoomId);
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
					xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), 
							XmppErrorType.CANCEL, XmppErrorConditions.BAD_REQUEST, 
							"Receiver is not member of the group/room.");

					// Throwing a custom exception or a runtime exception stops the execution 
					// of the parent method effectively, replacing the 'return' statement.
					throw new RuntimeException("Receiver not found in room: " + nickname);
				});
	}

	private boolean isModerationCommand(String type, String xml) {
		return XmppMessageType.SET == XmppMessageType.fromString(type) 
				&& xml.contains("http://jabber.org/protocol/muc#admin");
	}

	private void handleArchiveError(ChannelHandlerContext ctx, String id, XmppPrincipal principal, Throwable e) {
		String fromJid = principal.getBareJid();

		if (e instanceof DuplicateKeyException) {
			// Duplicate stanza detected (idempotent case).
			// Client MUST ignore this error; used only to support safe retries.
			xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key");

		} else {
			xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.WAIT, 
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