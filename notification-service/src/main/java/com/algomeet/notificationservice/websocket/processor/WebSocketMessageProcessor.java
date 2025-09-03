package com.algomeet.notificationservice.websocket.processor;

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
	boolean doProcess(String payload);
}
