package com.algomeet.notificationservice.websocket.processor;

import org.springframework.web.socket.WebSocketSession;

public interface WebSocketMessageProcessor {
	default int getOrder() {
		return 0;
	}
	
	/**
	 * Indicator if message processed
	 * @param payload
	 * @return return true if message processed, otherwise false so that next processor
	 * will process the message.
	 */
	boolean doProcess(WebSocketSession session, String payload);
}
