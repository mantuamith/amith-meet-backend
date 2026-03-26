package com.algomeet.xmpp.chatservice.cluster.listener;


import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.cluster.dto.ClusterSyncMessage;
import com.algomeet.xmpp.chatservice.handler.LocalStanzaDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessageListener {	
	private final LocalStanzaDispatcher localStanzaDispatcher;

	public void onMessage(String rawMessage, String channel) {
		log.info("Received: {}", rawMessage);
		
		// De-serialize JSON message back to ClusterSyncMessage        
		ClusterSyncMessage message = convertToObject(rawMessage, ClusterSyncMessage.class);

		if(message != null) {			
			localStanzaDispatcher.handleRouting(message.getTo(), message.getFrom(), message.getId(), message.getPayload());
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
