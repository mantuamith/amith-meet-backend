package com.algomeet.xmpp.chatservice.routing.muc.events;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.StateStanzaParser;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the broadcast of presence updates when a member interacts with a MUC room.
 * This handler ensures that both the joining user and existing occupants are synchronized 
 * with the current room state according to XEP-0045 standards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucMemberOrdinaryPresenceEventHandler {

    private final ClusterMessagePublisher clusterMessagePublisher;
    private final MucMessageRouter mucMessageRouter;
    private final JidUtil jidUtil;

    /**
     * Handles the successful entry of a member into a room by broadcasting presence.
     * * @param ctx       The Netty channel context for the current session.
     * @param roomJid   The full JID of the room.
     * @param senderJid The real JID of the user who is sending the presence.
     * @param xml       The original incoming XML presence stanza.
     * @param group     The Data Transfer Object representing the current room state.
     * @param sender    The MUC member profile of the person joining.
     */
    public void handleMemberPresence(ChannelHandlerContext ctx, String roomJid, String senderJid, String xml, MucRoomDto group, MucMember sender) { 
    	 UserState newState = determineState(xml);
         if (newState == null) {return;}
         
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        String status = parseStatus(xml);
        
        // 1. Send "Self-Presence" back to the joiner.
        // XMPP clients require status code 110 to recognize their own nickname in the room.
        String selfPresenceXml = buildSelfPresenceSuccess(
        		roomBareJid, 
        		sender.getUserKey(), 
        		senderJid, 
        		sender.getRole(), 
        		newState, 
        		status);
        clusterMessagePublisher.convertAndSendToUser(
            UUID.randomUUID().toString(), 
            sender.getUserKey(), 
            sender.getUserKey(), 
            ChatType.GROUPCHAT, 
            selfPresenceXml
        );
        
        // 2. Broadcast the joiner's availability to all members in the room.
        // This includes updating the joiner's view of existing members (Synchronizing State).
        String availablePresence = buildOccupantPresence(
                roomBareJid, 
                sender.getUserKey(), 
                sender.getRole(), 
                newState,
                status
            );       
        mucMessageRouter.broadcastToOccupants(UUID.randomUUID().toString(), sender.getUserKey(), group, availablePresence, false);
        
        
        log.debug("Presence synchronization complete for user {} in room {}", sender.getUserKey(), roomBareJid);
    }
    
    /**
     * Constructs the specific presence stanza used to acknowledge the sender's own entry.
     * * @param roomBareJid The bare JID of the room (room@conference.domain).
     * @param userKey     The nickname used by the occupant.
     * @param userJid     The real JID of the occupant.
     * @param affiliation The persistent affiliation (e.g., owner, admin, member).
     * @return A formatted XML presence string with MUC status code 110.
     */
    private String buildSelfPresenceSuccess(String roomBareJid, String userKey, String userJid, String affiliation, UserState state, String status) {
        String role = MucRoleUtil.getMucRole(affiliation).getValue();

        return String.format(
                "<presence from='%s/%s' to='%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "    <status code='110'/>" + // 110: Inform user that presence refers to them
                "  </x>" +
                getPresenceStatusElements(state) +
                (StringUtils.hasText(status) ? "<status>" + status + "<status>" : "") + 
                "</presence>",
                roomBareJid, userKey, userJid, affiliation, role, userJid
        );
    }
    
    /**
     * Constructs a general presence stanza for broadcasting to other room occupants.
     * Note: Per XEP-0045, the real JID is typically only shared in non-anonymous rooms.
     * * @param roomBareJid The bare JID of the room.
     * @param userKey     The nickname of the user whose presence is being broadcast.
     * @param affiliation The affiliation of that user.
     * @return A formatted XML presence string.
     */
    private String buildOccupantPresence(String roomBareJid, String userKey, String affiliation, UserState state, String status) {
        String role = MucRoleUtil.getMucRole(affiliation).getValue();
        return String.format(
                "<presence from='%s/%s'>" +
                "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
                "  </x>" +
                getPresenceStatusElements(state) +
                (StringUtils.hasText(status) ? "<status>" + status + "<status>" : "") + 
                "</presence>",
                roomBareJid, userKey, affiliation, role
        );
    }  
    
    /**
	 * Internal helper to map UserState to XMPP <show> elements.
	 */
	private String getPresenceStatusElements(UserState state) {
		return switch (state) {
		case AWAY -> "<show>away</show>";
		case INACTIVE -> "<show>xa</show>";
		case DND -> "<show>dnd</show>";
		default -> "";
		};
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