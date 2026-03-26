package com.algomeet.xmpp.chatservice.handler;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSessionLifecycleHandler {
    private final UserSessionRegistry userSessionRegistry;
    private final OfflineMessageHandler offlineMessageHandler; // The new split-off component

    public void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        UserState newState = determineState(xml);

        if (newState != null) {
            userSessionRegistry.updateSessionStatus(principal.getUserKey(), principal.getSessionId(), newState);
            log.debug("User {} status -> {}", principal.getUsername(), newState);
        }
                
        // Trigger offline message pull on initial "available" presence
        if (isInitialPresence(xml)) {
            offlineMessageHandler.deliverOfflineMessages(ctx, principal);
        }
    }

    private boolean isInitialPresence(String xml) {
        return xml.startsWith("<presence") && !xml.contains("type='unavailable'");
    }

    private UserState determineState(String xml) {
        if (!xml.contains("<presence") && !xml.contains("<active") && 
            !xml.contains("<inactive") && !xml.contains("<gone")) return null;
        
        if (xml.contains("<show>dnd</show>")) return UserState.DND;
        if (xml.contains("<show>away</show>") || xml.contains("<show>xa</show>")) return UserState.AWAY;
        if (xml.contains("type='unavailable'") || xml.contains("<gone")) return UserState.GONE;
        if (xml.contains("<inactive")) return UserState.INACTIVE;
        
        return UserState.ACTIVE;
    }
}