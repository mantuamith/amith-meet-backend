package com.algomeet.chatservice.sync.subscriber;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.chatservice.sync.dto.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber {
    private final SimpMessagingTemplate messagingTemplate;

    public void onMessage(String message, String channel) {
    	log.info("Received: {}", message);
    	// De-serialize JSON back to ChatMessage        
    	ChatMessage chatMessage = convertToObject(message, ChatMessage.class);

    	if(chatMessage != null) { 
    		// Forward to WebSocket topic    	  
    		try {
    			messagingTemplate.convertAndSendToUser(chatMessage.getTo(), chatMessage.getDestination(), chatMessage.getPayload());
    		} catch(Exception ex) {
    			log.error("Error sending message to {}, destination {}, details: {}", chatMessage.getTo(), chatMessage.getDestination(), ex.getMessage(), ex);
    			
    			if (chatMessage.getFrom() != null) {
    				messagingTemplate.convertAndSendToUser(
    						chatMessage.getFrom(),
    						"/queue/errors",
    						"WebRTC signaling failed to deliver to: " + chatMessage.getTo()
    						);
    			}
    		}
    	}
    }
    
    private <T> T convertToObject(String json, Class<T> t) {
    	try {
    		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); // enables Java 8 Date/Time (Instant, LocalDateTime, etc.);
    		return mapper.readValue(json, t);
    	} catch(Exception ex) {
    		log.error("Error convering message to object {}, details: {}", json, ex.getMessage(), ex);
    	}
    	return null;
    }
}
