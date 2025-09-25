package com.algomeet.chatservice.config;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import com.algomeet.chatservice.service.UserSessionService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final UserSessionService userSessionService;

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
        	userSessionService.addSession(user.getName(), accessor.getSessionId());
            username = user.getName();
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
