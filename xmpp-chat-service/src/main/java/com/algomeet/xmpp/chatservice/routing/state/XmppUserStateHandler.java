package com.algomeet.xmpp.chatservice.routing.state;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.StateStanzaParser;
import com.algomeet.xmpp.chatservice.routing.chat.OfflineMessageHandler;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Manages the lifecycle and availability states of an XMPP session.</p>
 * 
 * <p>This handler is responsible for interpreting XMPP signals that affect a user's 
 * reachability and session status. It processes two primary types of updates:</p>
 * <ul>
 *     <li><b>Presence (XEP-0186):</b> Updates user availability (e.g., Available, DND, Away) 
 *         and triggers the initial "sync" of offline messages.</li>
 *     <li><b>Chat States (XEP-0085):</b> Monitors activity indicators (e.g., Active, 
 *         Inactive, Gone) to optimize session resource management.</li>
 * </ul>
 * 
 * <p><b>Key Lifecycle Logic:</b> When a user first sends an 'available' presence, 
 * this component delegates to {@link OfflineMessageHandler} to flush any messages 
 * that were stored while the user was disconnected.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppUserStateHandler {

    private final UserSessionRegistry userSessionRegistry;
    private final OfflineMessageHandler offlineMessageHandler;
    private final XmppUserPresenceHandler xmppUserPresenceHandler;

    /**
     * Processes incoming XML for status-related updates and lifecycle triggers.
     * It synchronizes the user's availability and activates the session by 
     * flushing the offline message queue upon the first valid presence signal.
     * 
     * @param ctx       The Netty {@link ChannelHandlerContext}.
     * @param principal The authenticated user identity.
     * @param xml       The raw XML stanza content.
     */
    public void processPresence(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        UserState newState = determineState(xml);

        if (newState == null) return;
        
        // 1. Sync the global session registry
        userSessionRegistry.updateSessionStatus(principal.getUserKey(), principal.getSessionId(), newState);
        
        // 2. Handle user notifying user presence to user contacts and group chat rooms, take not this method must
        //    be invoke after "userSessionRegistry.updateSessionStatus"
        xmppUserPresenceHandler.handleUserPresence(ctx, principal, newState, xml);
        
        // 3. Lifecycle Activation (Offline Catch-up)
        if (newState != UserState.GONE) {
        	
            Attribute<Boolean> initialPresenceAttr = ctx.channel().attr(XmppSessionAttributes.INITIAL_PRESENCE_SENT);

            // Standard XMPP logic: Only trigger catch-up on the first non-MUC presence
            if (!xml.contains("http://jabber.org/protocol/muc") && !Boolean.TRUE.equals(initialPresenceAttr.get())) {
                
            	offlineMessageHandler.deliverOfflineMessages(ctx, principal);
            	
                initialPresenceAttr.set(true);
                log.info("Session activation for {}. Delivering missed content.", principal.getUserKey());                
            }
        }
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
}