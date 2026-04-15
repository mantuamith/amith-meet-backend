package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucCommandUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler responsible for processing requests to "mute" an occupant.
 * In XMPP MUC terms, muting is the act of revoking voice by changing 
 * an occupant's role to 'visitor'.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucMuteEventHandler {
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final MucMessageRouter mucMessageRouter;
	
	/**
	 * Processes a request to revoke an occupant's voice.
	 * * @param ctx       The Netty context for the moderator's session.
	 * @param roomJid   The JID of the room (room@conference.domain).
	 * @param senderJid The real JID of the moderator performing the mute.
	 * @param xml       The original IQ request XML.
	 * @param group     The MUC room data object.
	 * @param sender    The MUC profile of the moderator.
	 */
	public void handleMuteRequest(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
		// 1. Parse request attributes
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String victimJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String reason = extractReason(xml);

		log.info("Admin {} attempting to mute {} from {}", senderJid, victimJid, roomJid);

		// 2. Identify the target occupant (victim) by their user key
		String victimUserKey = XmppUtil.getUserKey(victimJid);
		Optional<MucMember> victimOpt = group.getMembers().stream()
				.filter(m -> m.getUserKey() != null && m.getUserKey().equalsIgnoreCase(victimUserKey))
				.findFirst();        
		
		// 3. Authority Validation
		// Ensure the moderator has sufficient permission to mute the target
		if (victimOpt.isPresent() && !(MucCommandUtil.isAuthorized(sender, victimOpt.get()))) {        	
			XmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			return;
		}

		// 4. State Preparation
		String targetJid = jidUtil.getBareJid(victimOpt.get().getUserKey());
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		// Maintain existing affiliation while downgrading role
		String affiliation = MucAffiliation.fromString(victimOpt.get().getRole()).getValue();
		
		// 5. Build and Broadcast Presence
		// Status 104 specifically informs the client that their voice has been revoked.
		String mutePresence = buildMutePresence(roomBareJid, victimUserKey, affiliation, targetJid, senderJid, reason);

		mucMessageRouter.broadcastToOccupants(id, sender.getUserKey(), group, mutePresence);
		
		// 6. Acknowledge the success of the command to the moderator
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Mute successful: {} role changed to visitor in {}", victimUserKey, roomJid);
	}
	
	/**
	 * Transmits a standard IQ 'result' stanza to acknowledge successful processing.
	 */
	private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		ctx.writeAndFlush(new TextWebSocketFrame(resp));
	}
	
	/**
	 * Formats a Mute presence stanza (Role: visitor, Status: 104).
	 * * @param roomJid     Bare JID of the room.
	 * @param victimUserKey The User key of the muted occupant.
	 * @param affiliation The current affiliation of the occupant.
	 * @param targetJid   The real JID of the occupant.
	 * @param actor       The Jid of the moderator who performed the mute.
	 * @param reason      The reason for the mute.
	 * @return A formatted XML presence string.
	 */
	private String buildMutePresence(String roomJid, String victimUserKey, String affiliation, String targetJid, String actorJid, String reason) {
	    return String.format(
	            "<presence from='%s/%s'>" +
	            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
	            "    <item affiliation='%s' role='visitor' jid='%s'>" + // Downgrade to visitor role
	            "      <actor jid='%s'/>" + // Inform who performed the action
	            "      <reason>%s</reason>" +
	            "    </item>" +
	            "    <status code='104'/>" + // 104: Inform occupant that voice has been revoked
	            "  </x>" +
	            "</presence>",
	            roomJid, victimUserKey, affiliation, targetJid, actorJid, reason
	    );
	}
	
	/**
	 * Safely extracts the content of the <reason> element.
	 * Defaults to a generic message if the element is missing or empty.
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