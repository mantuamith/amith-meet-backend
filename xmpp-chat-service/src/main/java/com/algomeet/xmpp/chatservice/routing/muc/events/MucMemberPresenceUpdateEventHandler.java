package com.algomeet.xmpp.chatservice.routing.muc.events;

import org.springframework.stereotype.Component;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.StateStanzaParser;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

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
public class MucMemberPresenceUpdateEventHandler {
    private final MucMessageRouter mucMessageRouter;

    /**
     * Handles the successful entry of a member into a room by broadcasting presence.
     * * @param ctx       The Netty channel context for the current session.
     * @param roomJid   The full JID of the room.
     * @param xml       The original incoming XML presence stanza.
     * @param group     The Data Transfer Object representing the current room state.
     * @param sender    The MUC member profile of the person joining.
     */
    public void handleMemberPresenceRequest(ChannelHandlerContext ctx, String roomJid, String xml, Group group, GroupMember sender) { 	 
     	UserState newState = determineState(xml);    	
        if (newState == null) {return;}
         
        String roomBareJid = XmppUtil.getRoomBareJid(roomJid);
        String status = parseStatus(xml);        
        
        // Broadcast the joiner's availability to all members in the room.
        // This includes updating the joiner's view of existing members (Synchronizing State).       
        String presenceXml = MucUserPresenceBuilder
				.create()
				.from(roomJid, sender.getUserKey()) // Resource-part is the member's room identity
				.show(newState.name().toString().toLowerCase())
				.affiliation(sender.getRole())
				.role(MucRole.fromString(sender.getRole()).getValue())
				.status(status)
				.build();
        
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();  
        mucMessageRouter.broadcastToOccupants(UuidCreator.getTimeOrderedEpoch().toString(), sender.getUserKey(), group, presenceXml, principal.getSessionId());
                
        log.debug("Presence synchronization complete for user {} in room {}", sender.getUserKey(), roomBareJid);
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