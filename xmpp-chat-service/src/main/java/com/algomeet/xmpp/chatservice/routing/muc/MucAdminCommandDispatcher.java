package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucAffiliation;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.GroupCacheService;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucCommandUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates administrative commands for Multi-User Chat (MUC) rooms.
 * <p>
 * This component handles high-privilege operations within the {@code http://jabber.org/protocol/muc#admin} 
 * namespace. It manages the business logic for occupant removal (kicking), voice management (muting/unmuting), 
 * and affiliation updates (member promotion).
 * </p>
 * <p>
 * <b>Architecture Role:</b> It validates moderator permissions, generates protocol-compliant presence broadcasts, 
 * and uses the {@link ClusterMessagePublisher} to ensure state changes are synchronized across all 
 * active cluster nodes.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucAdminCommandDispatcher {

	private final ClusterMessagePublisher clusterMessagePublisher;
	private final DomainProperties domainProperties;
	private final GroupCacheService groupCacheService;
	private final JidUtil jidUtil;

	/**
	 * Routes an incoming XML command stanza to the appropriate internal handler.
	 *
	 * @param ctx       The Netty channel context for the current session.
	 * @param roomJid   The JID of the target MUC room.
	 * @param senderJid The real JID of the user initiating the command.
	 * @param xml       The raw XML payload of the IQ stanza.
	 * @param group     The data transfer object representing the current room state.
	 * @param sender    The {@link MucMember} profile of the initiator for permission validation.
	 */
	public void handleCommandStanza(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucMember sender) {
		// Force refresh group cache
		MucRoomDto group = groupCacheService.getCachedGroup(Long.parseLong(XmppUtil.getRoomId(roomJid)), true);
				
		if (MucCommandUtil.isKickPayload(xml)) {
			handleKickRequest(ctx, roomJid, senderJid, xml, group, sender);
		} else if (MucCommandUtil.isMutePayload(xml)) {
			handleMuteRequest(ctx, roomJid, senderJid, xml, group, sender);
		} else if (MucCommandUtil.isUnMutePayload(xml)) {
			handleUnMuteRequest(ctx, roomJid, senderJid, xml, group, sender);
		} else if (MucCommandUtil.isAddMemberStanza(xml)) {
			handleAddMember(ctx, roomJid, senderJid, xml, group, sender);
		}
	}

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
		String victimNick = XmppStanzaUtil.getAttribute(xml, "item", "nick");
		String reason = extractReason(xml);

		log.info("Admin {} attempting to kick {} from {}", senderJid, victimNick, roomJid);

		Optional<MucMember> victimOpt = group.getMembers().stream()
				.filter(m -> m.getNickname() != null && m.getNickname().equalsIgnoreCase(victimNick))
				.findFirst();        
		
		if (victimOpt.isPresent() && !(MucCommandUtil.isAuthorized(sender, victimOpt.get()))) {        	
			XmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			return;
		}

		String targetJid = jidUtil.getBareJid(victimOpt.get().getUserKey());
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		String kickPresence = buildKickPresence(roomBareJid, victimNick, targetJid, sender.getNickname(), reason);

		broadcastToRoom(id, sender.getUserKey(), group, kickPresence);
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Kick successful: {} removed from {}", victimNick, roomJid);
	}

	/**
	 * Processes a request to revoke an occupant's voice (mute).
	 * <p>
	 * Changes the occupant's role to {@code visitor} and broadcasts a presence update 
	 * containing status code <b>104</b> to signal the revocation of voice.
	 * </p>
	 */
	public void handleMuteRequest(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String victimNick = XmppStanzaUtil.getAttribute(xml, "item", "nick");
		String reason = extractReason(xml);

		log.info("Admin {} attempting to mute {} from {}", senderJid, victimNick, roomJid);

		Optional<MucMember> victimOpt = group.getMembers().stream()
				.filter(m -> m.getNickname() != null && m.getNickname().equalsIgnoreCase(victimNick))
				.findFirst();        
		
		if (victimOpt.isPresent() && !(MucCommandUtil.isAuthorized(sender, victimOpt.get()))) {        	
			XmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			return;
		}

		String targetJid = jidUtil.getBareJid(victimOpt.get().getUserKey());
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		String affiliation = MucAffiliation.fromString(victimOpt.get().getRole()).getValue();
		String mutePresence = buildMutePresence(roomBareJid, victimNick, affiliation, targetJid, sender.getNickname(), reason);

		broadcastToRoom(id, sender.getUserKey(), group, mutePresence);
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Mute successful: {} muted from {}", victimNick, roomJid);
	}
	
	/**
	 * Processes a request to restore an occupant's voice (unmute).
	 * <p>
	 * Re-evaluates the user's role based on affiliation (usually back to {@code participant}) 
	 * and broadcasts a presence update with status code <b>110</b>.
	 * </p>
	 */
	public void handleUnMuteRequest(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String victimNick = XmppStanzaUtil.getAttribute(xml, "item", "nick");
		String reason = extractReason(xml);

		log.info("Admin {} attempting to unmute {} from {}", senderJid, victimNick, roomJid);

		Optional<MucMember> victimOpt = group.getMembers().stream()
				.filter(m -> m.getNickname() != null && m.getNickname().equalsIgnoreCase(victimNick))
				.findFirst();        
		
		if (victimOpt.isPresent() && !(MucCommandUtil.isAuthorized(sender, victimOpt.get()))) {        	
			XmppUtil.sendError(ctx, id, senderJid, domainProperties.getGroupChatDomain(), 
					XmppErrorType.AUTH, XmppErrorConditions.FORBIDDEN, "Error code 403");
			return;
		}
		
		String targetJid = jidUtil.getBareJid(victimOpt.get().getUserKey());
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		String affiliation = MucAffiliation.fromString(victimOpt.get().getRole()).getValue();
		String unmutePresence = buildUnmutePresence(roomBareJid, victimNick, affiliation, targetJid, sender.getNickname(), reason);

		broadcastToRoom(id, sender.getUserKey(), group, unmutePresence);
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Un-mute successful: {} unmuted from {}", victimNick, roomJid);
	}
	
	/**
	 * Updates a user's affiliation within the room (e.g., granting 'member' status).
	 * <p>
	 * This changes the persistent relationship between the user and the room, 
	 * typically allowing them to bypass "members-only" restrictions.
	 * </p>
	 */
	public void handleAddMember(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String newMemberJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String affiliation = XmppStanzaUtil.getAttribute(xml, "item", "affiliation");
		String reason = extractReason(xml);

		log.info("Admin {} add {} to {}", senderJid, newMemberJid, roomJid);
		String mucAffiliation = MucAffiliation.fromString(affiliation).getValue();
		
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		Optional<MucMember> newMemberOpt = group.getMembers().stream()
				.filter(m -> m.getUserKey() != null && m.getUserKey().equalsIgnoreCase(XmppUtil.getUserKey(newMemberJid)))
				.findFirst();  
		String addMemberPresence = buildMemberPromotionPresence(roomBareJid, newMemberOpt.get().getNickname(), mucAffiliation, newMemberJid, reason);
		
		broadcastToRoom(id, sender.getUserKey(), group, addMemberPresence);
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("New member: {} broadcasted from {}", newMemberJid, roomJid);
	}

	/**
	 * Iterates through all room occupants and publishes the presence update to the cluster.
	 */
	private void broadcastToRoom(String id, String senderKey, MucRoomDto group, String presence) {
		for(MucMember receiver : group.getMembers()) {
			clusterMessagePublisher.convertAndSendToUser(id, receiver.getUserKey(), senderKey, ChatType.GROUPCHAT, presence);
		}
	}

	/**
	 * Formats a 307 Kick presence stanza.
	 */
	private String buildKickPresence(String roomJid, String nick, String targetJid, String actor, String reason) {
		return String.format(
				"<presence from='%s/%s' type='unavailable'>" +
						"  <x xmlns='http://jabber.org/protocol/muc#user'>" +
						"    <item affiliation='none' role='none' jid='%s'>" +
						"      <actor nick='%s'/>" +
						"      <reason>%s</reason>" +
						"    </item>" +
						"    <status code='307'/>" + 
						"  </x>" +
						"</presence>",
						roomJid, nick, targetJid, actor, reason
				);
	}

	/**
	 * Formats a 104 Mute presence stanza.
	 */
	private String buildMutePresence(String roomJid, String nick, String affiliation, String targetJid, String actor, String reason) {
	    return String.format(
	            "<presence from='%s/%s'>" +
	            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
	            "    <item affiliation='%s' role='visitor' jid='%s'>" +
	            "      <actor nick='%s'/>" +
	            "      <reason>%s</reason>" +
	            "    </item>" +
	            "    <status code='104'/>" + 
	            "  </x>" +
	            "</presence>",
	            roomJid, nick, affiliation, targetJid, actor, reason
	    );
	}
	
	/**
	 * Formats an Unmute presence stanza.
	 */
	private String buildUnmutePresence(String roomJid, String nick, String affiliation, String targetJid, String actorNick, String reason) {
	    return String.format(
	            "<presence from='%s/%s'>" +
	            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
	            "    <item affiliation='%s' role='%s' jid='%s'>" +
	            "      <actor nick='%s'/>" +
	            "      <reason>%s</reason>" +
	            "    </item>" +
	            "    <status code='110'/>" + 
	            "  </x>" +
	            "</presence>",
	            roomJid, nick, affiliation, MucRoleUtil.getMucRole(affiliation).getValue(), targetJid, actorNick, reason
	    );
	}
	
	/**
	 * Formats a Presence stanza for member promotion/affiliation changes.
	 */
	private String buildMemberPromotionPresence(String roomJid, String nick, String affiliation, String targetJid,  String reason) {
	    return String.format(
	            "<presence from='%s/%s'>" +
	            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
	            "    <item affiliation='%s' role='%s' jid='%s'>" +
	            "      <reason>%s</reason>" +
	            "    </item>" +
	            "  </x>" +
	            "</presence>",
	            roomJid, nick, affiliation, MucRoleUtil.getMucRole(affiliation).getValue(), targetJid, reason
	    );
	}
	
	/**
	 * Transmits a standard IQ 'result' stanza to acknowledge successful processing of an admin command.
	 */
	private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		ctx.writeAndFlush(new TextWebSocketFrame(resp));
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