package com.algomeet.chatservice.config;

import java.security.Principal;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.*;

import com.algomeet.chatservice.service.UserSessionService;
import com.algomeet.chatservice.constant.Constants;
import com.algomeet.chatservice.service.GroupSessionMessageService;
import com.algomeet.chatservice.service.MessageService;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;


@Component
@Slf4j
@AllArgsConstructor
public class WebSocketEventListener {

    private final UserSessionService userSessionService;
    private final MessageService messageService;
    private final GroupSessionMessageService groupSessionMessageService;
    
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
        
        if (Constants.DESTINATION_KEYS_GROUP_SHAERE.equalsIgnoreCase(accessor.getDestination())) {
			try {
				// Deliver all pending group session messages
				groupSessionMessageService.deliverAllPendingTo(event.getUser().getName());
			} catch (Exception ex) {
				log.warn("[WS CONNECTED] group message session auto-deliver failed for user={}: {}", event.getUser().getName(), ex.getMessage());
			}
		}
    }

    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        log.info("[WS UNSUBSCRIBE] Session ID: {}, Destination: {}", accessor.getSessionId(), accessor.getDestination());
    }
}
