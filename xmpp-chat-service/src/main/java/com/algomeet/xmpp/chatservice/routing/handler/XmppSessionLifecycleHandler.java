package com.algomeet.xmpp.chatservice.routing.handler;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;

import io.netty.channel.ChannelHandlerContext;
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
public class XmppSessionLifecycleHandler {

    private final UserSessionRegistry userSessionRegistry;
    private final OfflineMessageHandler offlineMessageHandler;

    /**
     * Processes incoming XML for status-related updates and lifecycle triggers.
     * 
     * @param ctx       The Netty {@link ChannelHandlerContext}.
     * @param principal The authenticated user identity.
     * @param xml       The raw XML stanza content.
     */
    public void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        UserState newState = determineState(xml);

        // 1. Update the global session registry if a state change is detected
        if (newState != null) {
            userSessionRegistry.updateSessionStatus(principal.getUserKey(), principal.getSessionId(), newState);
            log.debug("User {} (Session: {}) status updated to -> {}", 
                principal.getUsername(), principal.getSessionId(), newState);
        }
                
        // 2. Trigger "Catch-up" phase: deliver messages sent while user was offline
        // This occurs exactly once when the client signals they are ready to receive data.
        if (isInitialPresence(xml)) {
            log.info("Initial presence detected for {}. Triggering offline message delivery.", principal.getUserKey());
            offlineMessageHandler.deliverOfflineMessages(ctx, principal);
        }
    }

    /**
     * Identifies if the stanza is an initial 'available' presence.
     * Per XMPP spec, a presence without a 'type' attribute implies availability.
     */
    private boolean isInitialPresence(String xml) {
        return xml.startsWith("<presence") && !xml.contains("type='unavailable'");
    }

    /**
     * Light-weight XML parsing to map XMPP elements to internal {@link UserState}.
     * 
     * @param xml The raw stanza XML.
     * @return The corresponding {@link UserState}, or null if the stanza contains no state info.
     */
    private UserState determineState(String xml) {
        // Guard clause: ignore stanzas that are not Presence or Chat State notifications
        if (!xml.contains("<presence") && !xml.contains("<active") && 
            !xml.contains("<inactive") && !xml.contains("<gone")) {
            return null;
        }
        
        // Map XEP-0186 Presence 'show' elements
        if (xml.contains("<show>dnd</show>")) return UserState.DND;
        if (xml.contains("<show>away</show>") || xml.contains("<show>xa</show>")) return UserState.AWAY;
        
        // Map XEP-0186/XEP-0085 termination signals
        if (xml.contains("type='unavailable'") || xml.contains("<gone")) return UserState.GONE;
        
        // Map XEP-0085 Chat States
        if (xml.contains("<inactive")) return UserState.INACTIVE;
        
        // Default to Active if it is a presence/active stanza not covered by specific checks
        return UserState.ACTIVE;
    }
}