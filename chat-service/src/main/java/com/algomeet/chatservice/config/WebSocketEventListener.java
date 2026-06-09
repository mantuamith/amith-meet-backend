package com.algomeet.chatservice.config;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import com.algomeet.chatservice.service.MessageService;
import com.algomeet.chatservice.service.UserSessionService;
import com.algomeet.chatservice.config.StompUserPrincipal;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
@AllArgsConstructor
public class WebSocketEventListener {

    private final UserSessionService userSessionService;
    private final MessageService messageService;
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[WS CONNECT] Session ID: {}", accessor.getSessionId());
    }

    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = null;
        Principal user = accessor.getUser();
        if (user != null) {
            String userKey = (user instanceof StompUserPrincipal sup) ? sup.userKey() : null;
            userSessionService.addSession(user.getName(), accessor.getSessionId(), userKey);
            username = user.getName();
            try {
                // NEW: auto-mark all SENT→DELIVERED for this user
                messageService.deliverAllPendingTo(username);
            } catch (Exception ex) {
                log.warn("[WS CONNECTED] auto-deliver failed for user={}: {}", username, ex.getMessage());
            }
        }
        
        log.info("[WS CONNECTED] Session ID: {}, User name: {}", accessor.getSessionId(), username);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
    	StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String username = null;
        if (user != null) {
        	userSessionService.removeSession(user.getName(), accessor.getSessionId());
            username = user.getName();
        }
        
        log.info("[WS DISCONNECT] Session ID: {}, User name: {}", event.getSessionId(), username);
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[WS SUBSCRIBE] Session ID: {}, Destination: {}", accessor.getSessionId(), accessor.getDestination());
        
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[WS UNSUBSCRIBE] Session ID: {}, Destination: {}", accessor.getSessionId(), accessor.getDestination());
    }
}
