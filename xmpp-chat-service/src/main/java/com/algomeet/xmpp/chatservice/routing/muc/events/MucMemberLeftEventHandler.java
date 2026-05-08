package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.PresenceType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.parser.StateStanzaParser;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.service.MucPresenceService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class MucMemberLeftEventHandler {
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final MucMessageRouter mucMessageRouter;
    private final JidUtil jidUtil;
    private final DomainProperties domainProperties;
	/**
	 * Handles MUC message fanout to all occupants.
	 */
	private final MucMessageRouter xmppBroadCastHandler;

	/**
	 * Archives messages for Message Archive Management (MAM).
	 */
	private final XmppArchiveService xmppArchiveService;
    
    /**
     * Handles the successful entry of a member into a room by broadcasting presence.
     * * @param ctx       The Netty channel context for the current session.
     * @param roomJid   The full JID of the room.
     * @param xml       The original incoming XML presence stanza.
     * @param group     The Data Transfer Object representing the current room state.
     * @param sender    The MUC member profile of the person joining.
     */
    public void handleMemberLeftRoom(ChannelHandlerContext ctx, String roomJid, String xml, MucRoomDto group, MucMember sender) { 	 
     	UserState newState = determineState(xml);    	
        if (newState == null) {return;}
         
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        String status = parseStatus(xml);
        
        // 1. Send "Self-Presence" back to the joiner.
        // XMPP clients require status code 110 to recognize their own nickname in the room.        
        String selfPresenceXml = MucUserPresenceBuilder
        		.create()
        		.from(roomJid, sender.getUserKey()) // Resource-part is the member's room identity
        		.type(PresenceType.UNAVAILABLE.getValue())
				.affiliation(sender.getRole())
				.role(MucRole.NONE.getValue())
				.status(status)
				.statusCode(110)
        		.build();
        
        clusterMessagePublisher.convertAndSendToUser(
            UUID.randomUUID().toString(), 
            sender.getUserKey(), 
            sender.getUserKey(), 
            ChatType.GROUPCHAT, 
            selfPresenceXml
        );
        
        // 2. Broadcast the joiner's availability to all members in the room.
        // This includes updating the joiner's view of existing members (Synchronizing State).       
        String presenceXml = MucUserPresenceBuilder
				.create()
				.type(PresenceType.UNAVAILABLE.getValue())
				.from(roomJid, sender.getUserKey()) // Resource-part is the member's room identity
				.affiliation(sender.getRole())
				.role(MucRole.NONE.getValue())
				.status(status)
				.build();
        
        mucMessageRouter.broadcastToOccupants(UUID.randomUUID().toString(), sender.getUserKey(), group, presenceXml, false);
        
        /**
		 * ----------------------------------------------------------
		 * Build system log message
		 * ----------------------------------------------------------
		 * Human-readable audit trail message.
		 */
		String messageId = UUID.randomUUID().toString();

		String body =
				sender.getUsername()
				+ " left ";
	
		String senderJid = jidUtil.getBareJid(sender.getUserKey());
        String xmlLogStanza = buildMemberLeftLogStanza(
        		messageId,
        		senderJid,
				roomBareJid,
				body,
				senderJid
				);

		/**
		 * ----------------------------------------------------------
		 * Persist event (Message Archive Management)
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
				forArchiveXmlLog
				);
        
        log.debug("User left the room presence synchronization complete for user {} in room {}", sender.getUserKey(), roomBareJid);
    }
    
    private String buildMemberLeftLogStanza(
			String id,
			String fromJid,
			String roomJid,
			String body,
			String newlyAddedUserJid) {

		return String.format(
				"<message id='%s' from='%s' to='%s' type='groupchat'>" +
						"  <body>%s</body>" +
						"  <x xmlns='http://algomeet.app/protocol/system'>" +
						"    <event type='member_left' jid='%s'/>" +
						"  </x>" +
						"</message>",
						id,
						fromJid,
						roomJid,
						body,
						newlyAddedUserJid
				);
	}
    
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
				ulidString
				);
	}
            
    private UserState determineState(String xml) {
    	// Guard clause: ignore stanzas that are not Presence or Chat State notifications
    	try {    		
    		if (XmppStanzaUtil.isPresenceStanza(xml)) {
    			return StateStanzaParser.determineState(xml);
    		}
    	} catch(Exception ex) {
    		// Silent error
    	}
    	
    	return null;
    }
    
    /**
    * Extract the value between <status> tags using fast string indexing.
    * This avoids the overhead of a full XML parser for simple presence payloads.
    *
    * @param xml The raw presence stanza.
    * @return The status text, or null if not found.
    */
   public static String parseStatus(String xml) {
       if (xml == null) return null;

       int startTag = xml.indexOf("<status>");
       if (startTag == -1) return null;

       int endTag = xml.indexOf("</status>", startTag);
       if (endTag == -1) return null;

       // Offset by 8 to skip past the length of "<status>"
       return xml.substring(startTag + 8, endTag).trim();
   }
}