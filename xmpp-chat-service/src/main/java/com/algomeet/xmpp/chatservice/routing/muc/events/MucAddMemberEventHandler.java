package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.MucPresenceService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.UserStateUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MUC Admin Event Handler: Add Member to Room
 * ==========================================================
 *
 * This handler processes administrative IQ stanzas that:
 *
 * - Add a user to a Multi-User Chat (MUC) room
 * - Change user affiliation (none → member/admin/owner)
 * - Broadcast updated presence state to all occupants
 * - Persist the change in chat history (MAM)
 *
 * XEP References:
 * ----------------------------------------------------------
 * - XEP-0045: Multi-User Chat (core room logic)
 * - XEP-0359: Unique and Stable Stanza IDs (message tracking)
 *
 * High-Level Flow:
 * ----------------------------------------------------------
 * 1. Parse admin IQ request
 * 2. Resolve target user & room state
 * 3. Determine user session presence state
 * 4. Build MUC presence update stanza
 * 5. Broadcast presence to room occupants
 * 6. Create system log message (member added)
 * 7. Persist event into archive (MAM)
 * 8. Broadcast system message
 * 9. Respond success IQ to admin
 * 10. Push updated presence to target user devices
 *
 * IMPORTANT BEHAVIOR:
 * ----------------------------------------------------------
 * This operation is:
 * - Real-time (broadcast immediately)
 * - Event-sourced (archived after emission)
 * - Multi-device aware (session registry based)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucAddMemberEventHandler {

	private final DomainProperties domainProperties;
	private final UserSessionRegistry userSessionRegistry;
	private final MucMessageRouter xmppBroadCastHandler;
	private final XmppArchiveService xmppArchiveService;
	private final MucMessageRouter mucMessageRouter;
	private final JidUtil jidUtil;
	private final MucPresenceService mucPresenceService;
	private final LocalStanzaDispatcher localStanzaDispatcher;

	/**
	 * Handles the "add member" administrative action.
	 *
	 * @param ctx Netty channel context of admin request
	 * @param roomJid full room JID (room@conference.domain)
	 * @param xml original IQ stanza payload
	 * @param group current room state snapshot
	 * @param sender admin user performing the action
	 */
	public void handleAddMemberRequest(
			ChannelHandlerContext ctx,
			String roomJid,
			String xml,
			MucRoomDto group,
			MucMember sender) {

		/**
		 * ----------------------------------------------------------
		 * 1. Extract IQ payload fields
		 * ----------------------------------------------------------
		 * Example:
		 * <item jid='user@domain' affiliation='member'/>
		 */
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String newMemberJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String affiliation = XmppStanzaUtil.getAttribute(xml, "item", "affiliation");
		String reason = extractReason(xml);
		String senderJid = jidUtil.getBareJid(sender.getUserKey());

		log.info("Admin {} adding {} to {} with affiliation {}",
				senderJid,
				newMemberJid,
				roomJid,
				affiliation);

		/**
		 * Convert affiliation string into enum-safe value.
		 */
		String newMemberMucAffiliation = MucAffiliation.fromString(affiliation).getValue();
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);

		/**
		 * ----------------------------------------------------------
		 * 2. Resolve member inside room state
		 * ----------------------------------------------------------
		 * Ensures user exists in system before processing.
		 */
		Optional<MucMember> newMemberOpt =
				group.getMembers().stream()
				.filter(m ->
				m.getUserKey() != null
				&& m.getUserKey().equalsIgnoreCase(
						XmppUtil.getUserKey(newMemberJid)
						))
				.findFirst();

		/**
		 * NOTE:
		 * In production systems, absence here usually means:
		 * - stale room state
		 * - race condition during membership update
		 */
		Set<UserSession> newMemberSessions = userSessionRegistry.getSessions(
				newMemberOpt.get().getUserKey());

		/**
		 * ----------------------------------------------------------
		 * 3. Determine presence state across devices
		 * ----------------------------------------------------------
		 * Multi-device logic:
		 * - ONLINE if any session active
		 * - otherwise GONE/OFFLINE
		 */
		String newMemberUserKey = XmppUtil.getUserKey(newMemberJid);

		UserState newMemberState = UserState.GONE;
		long updatedAt = 0L;

		if (!newMemberSessions.isEmpty()) {

			newMemberState = UserStateUtil.determineOverallState(
					newMemberSessions);

			/**
			 * Take latest activity across all devices.
			 */
			updatedAt =
					newMemberSessions.stream()
					.mapToLong(UserSession::getUpdatedAt)
					.max()
					.orElse(0L);
		}

		/**
		 * ----------------------------------------------------------
		 * 4. Build MUC presence update stanza
		 * ----------------------------------------------------------
		 * Broadcasts updated role/affiliation to all occupants.
		 */
		String presenceXml =
				MucUserPresenceBuilder.create()
				.from(roomJid, newMemberUserKey)
				.show(newMemberState.name().toLowerCase())
				.affiliation(newMemberMucAffiliation)
				.role(
						MucRole.fromString(
								newMemberMucAffiliation
								).getValue()
						)
				.targetJid(jidUtil.getBareJid(newMemberUserKey))
				.updatedAt(
						Instant.ofEpochMilli(updatedAt).toString()
						)
				.reason(reason)
				.build();

		/**
		 * Broadcast presence update to ALL room occupants.
		 */
		mucMessageRouter.broadcastToOccupants(
				id,
				sender.getUserKey(),
				group,
				presenceXml,
				true);

		/**
		 * ----------------------------------------------------------
		 * 5. Build system log message
		 * ----------------------------------------------------------
		 * Human-readable audit trail message.
		 */
		String messageId = UUID.randomUUID().toString();

		String body =
				sender.getUsername()
				+ " added "
				+ newMemberOpt.get().getUsername();

		String xmlLogStanza = buildMemberAddedLogStanza(
				messageId,
				senderJid,
				roomBareJid,
				body,
				newMemberJid);

		/**
		 * ----------------------------------------------------------
		 * 6. Persist event (Message Archive Management)
		 * ----------------------------------------------------------
		 * Ensures historical traceability of room changes.
		 */
		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		// Insert stanza ID
		String forArchiveXmlLog = XmppStanzaUtil.insertStanzaId(xmlLogStanza, ulidString, domainProperties.getDomain());
		
		saveToDatabase(messageId, roomBareJid, senderJid, group,	sender,	ulidString, forArchiveXmlLog);

		/**
		 * ----------------------------------------------------------
		 * 7. Broadcast system message to room
		 * ----------------------------------------------------------
		 * This is visible chat history event.
		 */
		xmppBroadCastHandler.broadcastToOccupants(
				ctx,
				messageId,
				roomJid,
				senderJid,
				XmppMessageType.GROUPCHAT,
				group,
				sender,
				null,
				forArchiveXmlLog);

		/**
		 * ----------------------------------------------------------
		 * 8. Send IQ success response to admin
		 * ----------------------------------------------------------
		 * Confirms operation completed successfully.
		 */
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		/**
		 * ----------------------------------------------------------
		 * 9. Push updated presence to target user devices
		 * ----------------------------------------------------------
		 * Ensures newly added member sees room state immediately.
		 */
		if (!CollectionUtils.isEmpty(newMemberSessions)) {
			mucPresenceService.pushGroupParticipantsPresenceToUser(
					ctx,
					group,
					newMemberUserKey);
		}

		log.info("Successfully promoted {} in room {}",
				newMemberJid,
				roomJid);
	}

	/**
	 * Archives membership change with XEP-0359 stanza-id injection.
	 */
	private void saveToDatabase(
			String id,
			String roomBareJid,
			String senderJid,
			MucRoomDto group,
			MucMember sender,
			String ulidString,
			String xml) {

		StanzaInfo info = StanzaInfo.builder()
				.messageId(id)
				.stanzaType(
						XmppMessageType.GROUPCHAT.getXmlValue()
						)
				.build();

		xmppArchiveService.archiveEvent(
				xml,
				info,
				XmppUtil.getRoomId(roomBareJid),
				null,
				sender.getUserKey(),
				ulidString);
	}

	/**
	 * Builds structured system message for member addition.
	 */
	private String buildMemberAddedLogStanza(
			String id,
			String fromJid,
			String roomJid,
			String body,
			String newlyAddedUserJid) {

		return String.format(
				"<message id='%s' from='%s' to='%s' type='groupchat'>" +
						"  <body>%s</body>" +
						"  <x xmlns='http://algomeet.app/protocol/system'>" +
						"    <event type='member_added' jid='%s'/>" +
						"  </x>" +
						"</message>",
						id,
						fromJid,
						roomJid,
						body,
						newlyAddedUserJid
				);
	}

	/**
	 * Sends IQ result response confirming success.
	 */
	private void sendSuccessResponse(
			ChannelHandlerContext ctx,
			String to,
			String from,
			String id) {

		String resp =
				String.format("<iq from='%s' to='%s' id='%s' type='result'/>",
						from, to, id);

		localStanzaDispatcher.dispatchLocally(to, from, resp);
	}

	/**
	 * Extracts <reason> field safely from raw XML.
	 */
	private String extractReason(String xml) {
		if (!xml.contains("<reason>"))
			return "No reason provided";

		return xml.substring(xml.indexOf("<reason>") + 8,
				xml.indexOf("</reason>"));
	}
}