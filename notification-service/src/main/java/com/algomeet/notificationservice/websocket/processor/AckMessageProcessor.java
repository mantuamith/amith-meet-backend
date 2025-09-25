package com.algomeet.notificationservice.websocket.processor;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.dto.NotificationAckMessage;
import com.algomeet.notificationservice.enums.MessageType;
import com.algomeet.notificationservice.service.UserNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AckMessageProcessor implements WebSocketMessageProcessor{
	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private UserNotificationService userNotificationService;

	@Override
	public void doProcess(WebSocketSession session, String payload, MessageType messageType) {
		if(!(StringUtils.hasLength(payload))) {
			return;
		}
		//Acknowledge message
		NotificationAckMessage ack = convertToObject(payload, NotificationAckMessage.class);
		if (Objects.nonNull(ack)) {
			log.info("ACK Status: {}, NotificationId: {}",  ack.getStatus(), ack.getNotificationId());

			userNotificationService.markAsDelivered(ack.getNotificationId());
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
		return MessageType.ACK ;
	}
}
