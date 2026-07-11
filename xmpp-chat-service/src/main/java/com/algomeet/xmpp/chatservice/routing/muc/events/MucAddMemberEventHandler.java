package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.MucEventType;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.MucPresenceService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.events.MucSystemEventLogMessageStanza;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;
import com.algomeet.xmpp.chatservice.util.UserStateUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
	private final XmppUtil xmppUtil;

	/**
	 * Handles the "add member" administrative action.
	 *
	 * @param ctx Netty channel context of admin request
	 * @param roomJid full room JID (room@conference.domain)
	 * @param xml original IQ stanza payload
	 * @param group current room state snapshot
	 * @param sender admin user performing the action
	 * @return A {@link Mono<Void>} signaling completion of the add member flow.
	 */
	public Mono<Void> handleAddMemberRequest(
			ChannelHandlerContext ctx,
			String roomJid,
			String xml,
			Group group,
			GroupMember sender) {

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
		Optional<GroupMember> newMemberOpt = SearchUtil.findMember(group, XmppUtil.getUserKey(newMemberJid));
		
		// Prerequisite: the member must have already been added to the group using group-service API.
		if (newMemberOpt.isEmpty()) {        	
			xmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			log.error("Error code 403 adding new member {} to room {} by {}", newMemberJid, roomJid, senderJid);
			return Mono.empty();
		}

		String newMemberUserKey = XmppUtil.getUserKey(newMemberJid);

		/**
		 * NOTE:
		 * In production systems, absence here usually means:
		 * - stale room state
		 * - race condition during membership update
		 */
		return userSessionRegistry.getSessions(newMemberOpt.get().getUserKey())
				.flatMap((Set<UserSession> newMemberSessions) -> {

					/**
					 * ----------------------------------------------------------
					 * 3. Determine presence state across devices
					 * ----------------------------------------------------------
					 * Multi-device logic:
					 * - ONLINE if any session active
					 * - otherwise GONE/OFFLINE
					 */
					UserState newMemberState = UserState.GONE;
					long updatedAt = 0L;

					if (!newMemberSessions.isEmpty()) {
						newMemberState = UserStateUtil.determineOverallState(newMemberSessions);

						/**
						 * Take latest activity across all devices.
						 */
						updatedAt = newMemberSessions.stream()
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
					String presenceXml = MucUserPresenceBuilder.create()
							.from(roomBareJid, newMemberUserKey)
							.show(newMemberState.name().toLowerCase())
							.affiliation(newMemberMucAffiliation)
							.role(MucRole.fromString(newMemberMucAffiliation).getValue())
							.targetJid(jidUtil.getBareJid(newMemberUserKey))
							.updatedAt(Instant.ofEpochMilli(updatedAt).toString())
							.reason(reason)
							.build();

					/**
					 * ----------------------------------------------------------
					 * 5. Build system log message
					 * ----------------------------------------------------------
					 * Human-readable audit trail message.
					 */
					String messageId = UuidCreator.getTimeOrderedEpoch().toString();
					String body = sender.getUsername() + " added " + newMemberOpt.get().getUsername();

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
					UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
					String forArchiveXmlLog = XmppStanzaUtil.insertStanzaId(xmlLogStanza, stanzaId.toString(), domainProperties.getDomain());

					// Sequential orchestration of all broadcasts and storage pipelines
					return mucMessageRouter.broadcastToOccupants(id, sender.getUserKey(), group, presenceXml)
							.then(Mono.defer(() -> saveToDatabaseReactive(messageId, roomBareJid, sender, stanzaId, forArchiveXmlLog, group.getMessageRetentionDays())))
							.then(Mono.defer(() -> xmppBroadCastHandler.broadcastToOccupants(
									ctx,
									messageId,
									roomJid,
									senderJid,
									XmppMessageType.GROUPCHAT,
									group,
									null,
									forArchiveXmlLog)))
							.then(Mono.defer(() -> sendSuccessResponseReactive(ctx, senderJid, roomJid, id)))
							.then(Mono.defer(() -> {
								/**
								 * ----------------------------------------------------------
								 * 9. Push updated presence to target user devices
								 * ----------------------------------------------------------
								 * Ensures newly added member sees room state immediately.
								 */
								if (!CollectionUtils.isEmpty(newMemberSessions)) {
									return mucPresenceService.pushGroupParticipantsPresenceToUser(
											ctx,
											group,
											newMemberUserKey);
								}
								return Mono.empty();
							}))
							.doOnSuccess(unused -> log.info("Successfully promoted {} in room {}", newMemberJid, roomJid));
				});
	}

	/**
	 * Archives membership change with XEP-0359 stanza-id injection.
	 */
	private Mono<Void> saveToDatabaseReactive(
			String id,
			String roomBareJid,
			GroupMember sender,
			UUID stanzaId,
			String xml,
			Integer messageRetentionDays) {

		return xmppArchiveService.archiveEvent(
				xml,
				id,
				XmppUtil.getRoomId(roomBareJid),
				null,
				sender.getUserKey(),
				stanzaId, 
				messageRetentionDays)
		.subscribeOn(Schedulers.boundedElastic()) // <-- REQUIRED to offload DB I/O
		.doOnError(e -> log.error("Failed to archive event", e)) // <-- Always catch errors
		.then();
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
		
		return MucSystemEventLogMessageStanza.builder()
				.id(id)
				.from(fromJid)
				.to(roomJid)
				.body(body)
				.eventType(MucEventType.MEMBER_ADDED)
				.eventJid(newlyAddedUserJid)
				.build()
				.toXml();
	}

	/**
	 * Sends IQ result response confirming success.
	 */
	private Mono<Void> sendSuccessResponseReactive(
			ChannelHandlerContext ctx,
			String to,
			String from,
			String id) {

		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		return localStanzaDispatcher.dispatchLocally(to, from, resp).then();
	}

	/**
	 * Extracts <reason> field safely from raw XML.
	 */
	private String extractReason(String xml) {
	    int start = xml.indexOf("<reason>");
	    int end = xml.indexOf("</reason>");
	    if (start == -1 || end == -1 || end <= start) {
	        return "No reason provided";
	    }
	    return xml.substring(start + 8, end);
	}
}