package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class MucKickEventHandler {
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;
	private final MucMessageRouter mucMessageRouter;
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
	public void handleKickRequest(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String victimJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String reason = extractReason(xml);

		log.info("Admin {} attempting to kick {} from {}", senderJid, victimJid, roomJid);

		String victimUserKey = XmppUtil.getUserKey(victimJid);
		Optional<MucMember> victimOpt = group.getMembers().stream()
				.filter(m -> m.getUserKey() != null && m.getUserKey().equalsIgnoreCase(victimUserKey))
				.findFirst();        
		
		if (victimOpt.isPresent() && !(MucCommandUtil.isAuthorized(sender, victimOpt.get()))) {        	
			XmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			return;
		}

		String targetJid = jidUtil.getBareJid(victimOpt.get().getUserKey());
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		String kickPresence = buildKickPresence(roomBareJid, victimUserKey, targetJid, senderJid, reason);

		mucMessageRouter.broadcastToOccupants(id, sender.getUserKey(), group, kickPresence);
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Kick successful: {} removed from {}", victimJid, roomJid);
	}
	
	/**
	 * Transmits a standard IQ 'result' stanza to acknowledge successful processing of an admin command.
	 */
	private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		ctx.writeAndFlush(new TextWebSocketFrame(resp));
	}
	
	/**
	 * Formats a 307 Kick presence stanza.
	 */
	private String buildKickPresence(String roomJid, String victimUserKey, String targetJid, String actorJid, String reason) {
		return String.format(
				"<presence from='%s/%s' type='unavailable'>" +
						"  <x xmlns='http://jabber.org/protocol/muc#user'>" +
						"    <item affiliation='none' role='none' jid='%s'>" +
						"      <actor jid='%s'/>" +
						"      <reason>%s</reason>" +
						"    </item>" +
						"    <status code='307'/>" + 
						"  </x>" +
						"</presence>",
						roomJid, victimUserKey, targetJid, actorJid, reason
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
	
}
