package com.algomeet.notificationservice.subscriber;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.consumer.processor.PushNotificationProcessor;
import com.algomeet.notificationservice.dto.PublishPushMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PushMessageSubscriber {
	@Autowired
	private PushNotificationProcessor pushNotificationProcessor;
	
    public void onMessage(String message, String channel) {
    	log.info("Received: {}", message);
    	PublishPushMessage PublishPushMessage = convertToObject(message, PublishPushMessage.class);
    	if(Objects.isNull(PublishPushMessage)) {
    		return;
    	}
    	
    	// Push the message to the web socket
    	pushNotificationProcessor.pushMessage(PublishPushMessage.getUserKey(), PublishPushMessage.getNotificationMessage());
    	
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
