package com.algomeet.notificationservice.websocket.processor;

import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.enums.MessageType;

public interface WebSocketMessageProcessor {
	MessageType getMessageType();
	
	void doProcess(WebSocketSession session, String payload, MessageType messageType);
}
