package com.algomeet.notificationservice.websocket.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.dto.PingMessage;
import com.algomeet.notificationservice.enums.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PingMessageProcessor implements WebSocketMessageProcessor{
	@Autowired
	private ObjectMapper mapper;
		
	@Override
	public void doProcess(WebSocketSession session, String payload) {
		if(!(StringUtils.hasLength(payload))) {
			return;
		}

		//Acknowledge message
		PingMessage ping = convertToObject(payload, PingMessage.class);
		if (ping != null) {
			log.info("Ping: {}",  ping.getTimestamp());
		}
	}
	
	private <T> T convertToObject(String json, Class<T> t) {
		try {
			return mapper.readValue(json, t);
		} catch(Exception ex) {
			log.error("Error convering message to object {}", ex.getMessage(), ex);
		}
		return null;
	}

	@Override
	public MessageType getMessageType() {
		return MessageType.PING ;
	}
}
