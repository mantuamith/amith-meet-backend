package com.algomeet.chatservice.sync.subscriber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.chatservice.sync.dto.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedisSubscriber {
	@Autowired
    private SimpMessagingTemplate messagingTemplate;

    public RedisSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void onMessage(String message, String channel) {
        // De-serialize JSON back to ChatMessage        
    	ChatMessage chatMessage = convertToObject(message, ChatMessage.class);
		
        // Forward to WebSocket topic
        messagingTemplate.convertAndSendToUser(chatMessage.getUser(), chatMessage.getDestination(), chatMessage.getPayload());
    }
    
    private <T> T convertToObject(String json, Class<T> t) {
    	try {
    		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); // enables Java 8 Date/Time (Instant, LocalDateTime, etc.);
    		return mapper.readValue(json, t);
    	} catch(Exception ex) {
    		log.error("Error convering message to object {}", ex.getMessage(), ex);
    	}
    	return null;
    }
}
