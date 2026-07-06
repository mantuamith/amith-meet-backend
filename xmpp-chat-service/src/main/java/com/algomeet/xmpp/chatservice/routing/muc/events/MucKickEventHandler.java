package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.MucEventType;
import com.algomeet.xmpp.chatservice.enums.PresenceStatusCode;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.publisher.ExitGroupMemberMediaCleanupEventPublisher;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.events.MucSystemEventLogMessageStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MucKickEventHandler {
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final MucMessageRouter mucMessageRouter;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final XmppUtil xmppUtil;
	private final MucMessageRouter xmppBroadCastHandler;
	private final XmppArchiveService xmppArchiveService;
	private final ExitGroupMemberMediaCleanupEventPublisher exitGroupMemberMediaCleanupEventPublisher;
	
	/**
	 * Processes a request to forcibly remove (kick) an occupant from the room.
	 * <p>
	 * This method validates that the sender has higher authority than the victim. Upon success, 
	 * it broadcasts a {@code type='unavailable'} presence with status code <b>307</b>.
	 * </p>
	 *
	 * @param ctx       Netty context.
	 * @param roomJid   Target room JID.
	 * @param senderJid Real JID of the moderator.
	 * @param xml       The request payload containing the target nick.
	 * @param group     The room DTO.
	 * @param sender    The moderator's profile.
	 */
	public void handleKickMemberRequest(ChannelHandlerContext ctx, String roomJid, String xml, Group group, GroupMember sender) {
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String victimJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String reason = extractReason(xml);
		String senderJid = jidUtil.getBareJid(sender.getUserKey());

		log.info("Admin {} attempting to kick {} from {}", senderJid, victimJid, roomJid);

		String victimUserKey = XmppUtil.getUserKey(victimJid);  
		Optional<GroupMember> victimOpt = SearchUtil.findMember(group, victimUserKey);
		
		// Prerequisite: the member must have already been removed from the group using group-service API.
		if (victimOpt.isPresent()) {        	
			xmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			
			log.error("Error code 403 removing member {} from room {} by {}.", victimJid, roomJid, senderJid);
			return;
		}

		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		String kickPresence = buildKickPresence(roomBareJid, victimUserKey, victimJid, senderJid, reason);
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();  
		mucMessageRouter.broadcastToOccupants(id, sender.getUserKey(), group, kickPresence, principal.getSessionId());
		sendSuccessResponse(ctx, senderJid, roomJid, id);
		
		 /**
		 * ----------------------------------------------------------
		 * Build system log message
		 * ----------------------------------------------------------
		 * Human-readable audit trail message.
		 */
		String messageId = UuidCreator.getTimeOrderedEpoch().toString();

		String body = sender.getUsername() + " removed";
	
        String xmlLogStanza = buildMemberRemovedLogStanza(
        		messageId,
        		senderJid,
				roomBareJid,
				body,
				victimJid);

		/**
		 * ----------------------------------------------------------
		 * Persist event (Message Archive Management)
		 * ----------------------------------------------------------
		 * Ensures historical traceability of room changes.
		 */
		UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
		
		// Insert stanza ID
		String forArchiveXmlLog = XmppStanzaUtil.insertStanzaId(xmlLogStanza, stanzaId.toString(), domainProperties.getDomain());
		
		saveToDatabase(messageId, roomBareJid, sender, stanzaId, forArchiveXmlLog, group.getMessageRetentionDays());

		/**
		 * ----------------------------------------------------------
		 * 7. Broadcast system message to room
		 * ----------------------------------------------------------
		 * This is visible chat history event.
		 */
		xmppBroadCastHandler.broadcastToOccupants(
				ctx,
				messageId,
				roomBareJid,
				senderJid,
				XmppMessageType.GROUPCHAT,
				group,
				null,
				forArchiveXmlLog);
		
		// Cleanup up member group messages media files		
		exitGroupMemberMediaCleanupEventPublisher.publish(
				UUID.fromString(XmppUtil.getRoomId(roomJid)), UUID.fromString(victimUserKey));

		log.info("Kick successful: {} removed from {}", victimJid, roomJid);
	}
	
	/**
	 * Transmits a standard IQ 'result' stanza to acknowledge successful processing of an admin command.
	 */
	private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		localStanzaDispatcher.dispatchLocally(to, from, resp).subscribe();
	}
	
	/**
	 * Formats a 307 Kick presence stanza.
	 */
	private String buildKickPresence(String roomJid, String victimUserKey, String victimJid, String actorJid, String reason) {
		return String.format(
				"<presence from='%s/%s' type='unavailable'>" +
						"  <x xmlns='http://jabber.org/protocol/muc#user'>" +
						"    <item affiliation='none' role='none' jid='%s'>" +
						"      <actor jid='%s'/>" +
						"      <reason>%s</reason>" +
						"    </item>" +
						"    <status code='%d'/>" + 
						"  </x>" +
						"</presence>",
						roomJid, victimUserKey, victimJid, actorJid, reason, PresenceStatusCode.KICKED.getCode()
				);
	}
	
	/**
	 * Extracts the content of the {@code <reason>} element from the XML string.
	 * * @param xml The XML payload.
	 * @return The reason string, or a default message if not found.
	 */
	private String extractReason(String xml) {
		if (!xml.contains("<reason>")) return "No reason provided";
		return xml.substring(xml.indexOf("<reason>") + 8, xml.indexOf("</reason>"));
	}
	
	  private String buildMemberRemovedLogStanza(
				String id,
				String fromJid,
				String roomJid,
				String body,
				String removedUserJid) {
		  
	    	return MucSystemEventLogMessageStanza.builder()
					.id(id)
					.from(fromJid)
					.to(roomJid)
					.body(body)
					.eventType(MucEventType.MEMBER_REMOVED)
					.eventJid(removedUserJid)
					.build()
					.toXml();	    	
		}
	    
	    private void saveToDatabase(
				String id,
				String roomBareJid,
				GroupMember sender,
				UUID stanzaId,
				String xml,
				Integer messageRetentionDays) {

			xmppArchiveService.archiveEvent(
					xml,
					id,
					XmppUtil.getRoomId(roomBareJid),
					null,
					sender.getUserKey(),
					stanzaId,
					messageRetentionDays);
		}
	
}
