package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucCommandUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler responsible for restoring "voice" to a muted occupant in a MUC room.
 * This class processes requests to change a user's role from 'visitor' back to a 
 * voice-enabled role (e.g., 'participant'), enabling them to send messages again.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucUnMuteEventHandler {
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final MucMessageRouter mucMessageRouter;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final XmppUtil xmppUtil;

	/**
	 * Processes a request to restore an occupant's voice (unmute).
	 * * @param ctx       The Netty channel context for the admin session.
	 * @param roomJid   The JID of the room (room@conference.domain).
	 * @param xml       The original IQ request XML containing the target JID/nick.
	 * @param group     The current MUC room state (DTO).
	 * @param sender    The MUC profile of the moderator.
	 */
	public void handleUnMuteRequest(ChannelHandlerContext ctx, String roomJid, String xml, MucRoomDto group, MucMember sender) {
		String senderJid = jidUtil.getBareJid(sender.getUserKey());
		
		// 1. Extract request details
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String victimJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String reason = extractReason(xml);

		log.info("Admin {} attempting to unmute {} from {}", senderJid, victimJid, roomJid);

		// 2. Identify the target member (victim) based on the provided JID
		String victimUserKey = XmppUtil.getUserKey(victimJid);
		Optional<MucMember> victimOpt = group.getMembers().stream()
				.filter(m -> m.getUserKey() != null && m.getUserKey().equalsIgnoreCase(victimUserKey))
				.findFirst();        
		
		// 3. Authorization Check
		// Ensure the moderator has the authority to unmute the target.
		if (victimOpt.isPresent() && !(MucCommandUtil.isAuthorized(sender, victimOpt.get()))) {        	
			xmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			return;
		}
		
		// 4. State Reconstruction
		String targetJid = jidUtil.getBareJid(victimOpt.get().getUserKey());
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		// Retrieve current affiliation to determine what their role should be restored to
		String affiliation = MucAffiliation.fromString(victimOpt.get().getRole()).getValue();
		
		// 5. Build and Broadcast Unmute Presence
		// This notifies all occupants that the user has regained voice.
		String unmutePresence = buildUnmutePresence(roomBareJid, victimUserKey, affiliation, targetJid, senderJid, reason);

		mucMessageRouter.broadcastToOccupants(id, sender.getUserKey(), group, unmutePresence, true);
		
		// 6. Send IQ Result back to the admin to confirm success
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Un-mute successful: {} voice restored in {}", victimUserKey, roomJid);
	}
	
	/**
	 * Formats an Unmute presence stanza.
	 * Includes status code 110 to inform the client the presence refers to their own state change.
	 * * @param roomJid     Bare JID of the room.
	 * @param victimUserKey  User Key of the user being unmuted.
	 * @param affiliation User's persistent affiliation.
	 * @param targetJid   Real JID of the user.
	 * @param actorJid   Jid of the admin who unmuted the user.
	 * @param reason      Optional text reason for unmuting.
	 * @return Formatted XML presence string.
	 */
	private String buildUnmutePresence(String roomJid, String victimUserKey, String affiliation, String targetJid, String actorJid, String reason) {
		// Use MucRoleUtil to dynamically determine the correct role (usually 'participant') based on affiliation
		String restoredRole = MucRoleUtil.getMucRole(affiliation).getValue();

	    return String.format(
	            "<presence from='%s/%s'>" +
	            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
	            "    <item affiliation='%s' role='%s' jid='%s'>" +
	            "      <actor jid='%s'/>" +
	            "      <reason>%s</reason>" +
	            "    </item>" +
	            "  </x>" +
	            "</presence>",
	            roomJid, victimUserKey, affiliation, restoredRole, targetJid, actorJid, reason
	    );
	}
	
	/**
	 * Transmits a standard IQ 'result' stanza to acknowledge successful processing.
	 */
	private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		localStanzaDispatcher.dispatchLocally(to, from, resp).subscribe();
	}

	/**
	 * Safely extracts the content of the <reason> element from the request XML.
	 */
	private String extractReason(String xml) {
		if (!xml.contains("<reason>")) return "No reason provided";
		try {
			return xml.substring(xml.indexOf("<reason>") + 8, xml.indexOf("</reason>"));
		} catch (Exception e) {
			return "No reason provided";
		}
	}	
}