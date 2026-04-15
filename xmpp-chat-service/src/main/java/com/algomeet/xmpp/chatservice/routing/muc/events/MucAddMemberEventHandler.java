package com.algomeet.xmpp.chatservice.routing.muc.events;

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
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler responsible for processing administrative requests to add members to a MUC room.
 * This class manages the transition of a user's affiliation (e.g., from 'none' to 'member')
 * and broadcasts the necessary presence updates and system logs to the room occupants.
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
	
	/**
	 * Processes a request to update a user's affiliation within the room.
	 * * @param ctx       Netty context for the moderator's connection.
	 * @param roomJid   The JID of the room where the action is occurring.
	 * @param senderJid The JID of the administrator/moderator performing the action.
	 * @param xml       The original IQ request XML.
	 * @param group     The current state of the room (DTO).
	 * @param sender    The MUC member profile of the administrator.
	 */
	public void handleAddMember(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) {
		// 1. Extract attributes from the incoming IQ stanza
		String id = XmppStanzaUtil.getAttribute(xml, "id");
		String newMemberJid = XmppStanzaUtil.getAttribute(xml, "item", "jid");
		String affiliation = XmppStanzaUtil.getAttribute(xml, "item", "affiliation");
		String reason = extractReason(xml);

		log.info("Admin {} adding {} to {} with affiliation {}", senderJid, newMemberJid, roomJid, affiliation);
		String mucAffiliation = MucAffiliation.fromString(affiliation).getValue();
		String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
		
		// 2. Identify the target member within the room structure
		Optional<MucMember> newMemberOpt = group.getMembers().stream()
				.filter(m -> m.getUserKey() != null && m.getUserKey().equalsIgnoreCase(XmppUtil.getUserKey(newMemberJid)))
				.findFirst();  
		
		// 3. Determine the current MUC Role. 
		// If the user is not currently online (no sessions), their role is 'none'.
		Set<UserSession> newMemberSessions = userSessionRegistry.getSessions(newMemberOpt.get().getUserKey());		
		String mucRole = CollectionUtils.isEmpty(newMemberSessions) ? MucRole.NONE.getValue() : MucRoleUtil.getMucRole(affiliation).getValue();
		
		// 4. Broadcast Presence Update
		// Informs all occupants that the user's affiliation has changed (XEP-0045).
		String addMemberPresence = buildMemberPromotionPresence(roomBareJid, mucAffiliation, mucRole, newMemberJid, reason);		
		mucMessageRouter.broadcastToOccupants(id, sender.getUserKey(), group, addMemberPresence, true);
		
		// 5. Generate and Broadcast System Log Message
		// Creates a human-readable "Admin added User" message for the chat history.
		String stanzaId = UUID.randomUUID().toString();
		String body = sender.getUsername() + " added " + newMemberOpt.get().getUsername();
		String xmlStanza = buildMemberAddedLogStanza(stanzaId, senderJid, roomBareJid, body, newMemberJid);
		
		// 6. Persistence
		// Archive the action for Message Archive Management (MAM) retrieval.
		saveToDatabase(stanzaId, roomBareJid, senderJid, group, sender, xml);
		
		// 7. Dispatch the system message to all online occupants
		xmppBroadCastHandler.broadcastToOccupants(ctx, stanzaId, roomJid, senderJid, XmppMessageType.GROUPCHAT, group, sender, null, xmlStanza, xmlStanza);
		
		// 8. Finalize the request with an IQ Result to the admin
		sendSuccessResponse(ctx, senderJid, roomJid, id);

		log.info("Successfully promoted {} in room {}", newMemberJid, roomJid);
	}
		
	/**
	 * Archives the affiliation change event.
	 * Injects a unique Stanza-ID (XEP-0359) for synchronization.
	 */
	private void saveToDatabase(String id, String roomBareJid, String senderJid, MucRoomDto group, MucMember sender, String xml) {
		StanzaInfo info = StanzaInfo.builder()
				.stanzaId(id)
				.stanzaType(XmppMessageType.GROUPCHAT.getXmlValue())
				.build();
		
		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		
		// XEP-0359: Unique and Stable Stanza IDs. Essential for reliable chat history.
		String stanzaIdExtension = String.format("<stanza-id xmlns='urn:xmpp:sid:0' by='%s' id='%s'/>", 
				domainProperties.getDomain(), ulidString);
		
		// Ensure the stanza-id is placed inside the message payload
		String enrichedXml = xml.replace("</message>", stanzaIdExtension + "</message>");

		xmppArchiveService.archiveEvent(enrichedXml, info, roomBareJid, null, 
				senderJid, ulidString);
	}
			
	/**
	 * Formats a Presence stanza to notify the room of an affiliation/role change.
	 * Includes the target JID and an optional reason.
	 */
	private String buildMemberPromotionPresence(String roomJid, String affiliation, String mucRole, String targetJid,  String reason) {
	    return String.format(
	            "<presence from='%s'>" +
	            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
	            "    <item affiliation='%s' role='%s' jid='%s'>" +
	            "      <reason>%s</reason>" +
	            "    </item>" +
	            "  </x>" +
	            "</presence>",
	            roomJid, affiliation, mucRole, targetJid, reason
	    );
	}
	
	/**
	 * Constructs a system message to log the addition of a member.
	 * Uses a custom Algomeet extension for structured event reporting.
	 */
	private String buildMemberAddedLogStanza(String id, String fromJid, String roomJid, String body, String newlyAddedUserJid) {
	    return String.format(
	            "<message id='%s' from='%s' to='%s' type='groupchat'>" +
	            "  <body>%s</body>" +
	            "  <x xmlns='http://algomeet.app/protocol/system'>" +
	            "    <event type='member_added' jid='%s'/>" +
	            "  </x>" +
	            "</message>",
	            id, fromJid, roomJid, body, newlyAddedUserJid
	    );
	}
	
	/**
	 * Transmits a standard IQ 'result' stanza to acknowledge successful processing.
	 */
	private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
		String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
		ctx.writeAndFlush(new TextWebSocketFrame(resp));
	}

	/**
	 * Safely extracts the text content from a <reason> element.
	 * Returns a default string if no reason was provided in the XML request.
	 */
	private String extractReason(String xml) {
		if (!xml.contains("<reason>")) return "No reason provided";
		return xml.substring(xml.indexOf("<reason>") + 8, xml.indexOf("</reason>"));
	}
}