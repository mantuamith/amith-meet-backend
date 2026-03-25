package com.algomeet.xmpp.chatservice.routing.handler;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class ChatStateNotificationHandler {
    private final UserSessionRegistry userSessionRegistry;
    private final OfflineMessageService offlineMessageService;
	/**
     * Detects Presence (<show>) and Chat States (<active>/<inactive>)
     */
    public void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        String userId = principal.getUserKey();
        String sessionId = principal.getSessionId();
        UserState newState = null;

        // 1. Handle Presence (<presence>)
        if (xml.contains("<presence")) {
            if (xml.contains("<show>dnd</show>")) {
                newState = UserState.DND;
            } else if (xml.contains("<show>away</show>") || xml.contains("<show>xa</show>")) {
                newState = UserState.AWAY;
            } else if (xml.contains("type='unavailable'")) {
                newState = UserState.GONE;
            } else {
                newState = UserState.ACTIVE;
            }
        } 
        
        // 2. Handle Chat States (<active>, <inactive>, <gone>)
        else if (xml.contains("<active")) {
            newState = UserState.ACTIVE;
        } 
        else if (xml.contains("<inactive")) {
            newState = UserState.INACTIVE;
        } 
        else if (xml.contains("<gone")) {
            newState = UserState.GONE;
        }

        // 3. Persist to Redis if a state change was detected
        if (newState != null) {
            userSessionRegistry.updateSessionStatus(userId, sessionId, newState);
            log.debug("Status for user {} updated to {}", principal.getUsername(), newState);
        }
                
        // 4. Detect Initial Presence and Pull offline messages.
        // Logic: If it's a presence, auto-push. If it's a MAM query, stream archive.
        if (xml.startsWith("<presence") && !xml.contains("type='unavailable'")) {
        	// Standard RFC 6121 behavior
        	deliverOfflineMessages(ctx, principal);
        }   
    }
    
    private void deliverOfflineMessages(ChannelHandlerContext ctx, XmppPrincipal principal) {
    	List<OfflineMessage> unsendMessages = offlineMessageService.getOfflineMessages(principal.getUserKey());
    	
    	// Send to client all offline messages
    	for (OfflineMessage message : unsendMessages) {
    		ctx.writeAndFlush(new TextWebSocketFrame(
    				wrapWithDelay(message.getStanzaXml(), message.getCreatedAt(),  principal)));
    	}
    }
    
    public String wrapWithDelay(String originalXml, Instant originalTimestamp, XmppPrincipal principal) {
        // Format: 2026-03-25T14:30:00Z
        String stamp = originalTimestamp.toString(); 
        String delayTag = String.format("<delay xmlns='urn:xmpp:delay' from='%s' stamp='%s'/>", principal.getFullJid(), stamp);
        
        // Insert the delay tag before the closing </message>
        return originalXml.replace("</message>", delayTag + "</message>");
    }
}
