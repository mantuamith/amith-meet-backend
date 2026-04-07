package com.algomeet.xmpp.chatservice.routing.muc;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MucCommandDispatcher {
    private final ClusterMessagePublisher clusterMessagePublisher;
    
    public void handleAdminStanza(ChannelHandlerContext ctx, String roomJid, String adminJid, String xml, MucRoomDto group, MucMember sender) {
    	if (isKickPayload(xml) ) {
    		handleKickRequest(ctx, roomJid, adminJid, xml, group, sender);
    	}
    }
    
    public static boolean isKickPayload(String xml) {
        if (xml == null) return false;
        
        // 1. Verify it is a 'set' (command) rather than a 'get' (query) or 'result'
        if (!xml.contains("type='set'") && !xml.contains("type=\"set\"")) {
            return false;
        }

        // 2. The "Kick" indicator: Setting a role to 'none' removes the user from the room
        // Note: We check for role='none' specifically within the context of MUC Admin
        return xml.contains("role='none'") || xml.contains("role=\"none\"");
    }

    /**
     * Entry point for IQ set stanzas targeting the MUC Admin namespace.
     */
    public void handleKickRequest(ChannelHandlerContext ctx, String roomJid, String adminJid, String xml, MucRoomDto group, MucMember sender) {
        // 1. Extract metadata using your optimized utility
        String id = XmppStanzaUtil.getAttribute(xml, "id");
        String victimNick = XmppStanzaUtil.getAttribute(xml, "item", "nick");
        String reason = extractReason(xml);

        log.info("Admin {} attempting to kick {} from {}", adminJid, victimNick, roomJid);

        // TODO: Integrate with group service API
        // 2. Permission Check (Pseudo-logic: check your Room Manager/DB)
        /*
        if (!(GroupRole.ADMIN == GroupRole.fromString(sender.getRole())
        		|| GroupRole.OWNER == GroupRole.fromString(sender.getRole()))) {
        	
        	XmppUtil.sendError(ctx, adminJid, roomJid, id, "forbidden", "Error code 403");
            return;
        }*/

        // 3. Construct the 307 Presence (The actual "Kick" signal)
        String kickPresence = buildKickPresence(roomJid, victimNick, sender.getUserKey(), reason);

        // 4. Broadcast to the Room
        // This stops the ringing/session for the victim and notifies everyone else
		for(MucMember receiverMucMember : group.getMembers()) {
			String toUserKey = receiverMucMember.getUserKey();
			
			// Live Delivery: Publish to the cluster for real-time delivery to active sessions
			clusterMessagePublisher.convertAndSendToUser(id, toUserKey, sender.getUserKey(), ChatType.GROUPCHAT, kickPresence);
		}

        // 5. Send IQ Result (Success acknowledgment to Admin)
        sendSuccessResponse(ctx, adminJid, roomJid, id);
        
        log.info("Kick successful: {} removed from {}", victimNick, roomJid);
    }

    private String buildKickPresence(String roomJid, String nick, String actor, String reason) {
        return String.format(
            "<presence from='%s/%s' type='unavailable'>" +
            "  <x xmlns='http://jabber.org/protocol/muc#user'>" +
            "    <item affiliation='none' role='none'>" +
            "      <actor nick='%s'/>" +
            "      <reason>%s</reason>" +
            "    </item>" +
            "    <status code='307'/>" + // 307 is the standard XMPP Kick code
            "  </x>" +
            "</presence>",
            roomJid, nick, actor, reason
        );
    }

    private void sendSuccessResponse(ChannelHandlerContext ctx, String to, String from, String id) {
        String resp = String.format("<iq from='%s' to='%s' id='%s' type='result'/>", from, to, id);
        ctx.writeAndFlush(new TextWebSocketFrame(resp));
    }

    private String extractReason(String xml) {
        // Simple extraction for <reason> tag
        if (!xml.contains("<reason>")) return "No reason provided";
        return xml.substring(xml.indexOf("<reason>") + 8, xml.indexOf("</reason>"));
    }
}