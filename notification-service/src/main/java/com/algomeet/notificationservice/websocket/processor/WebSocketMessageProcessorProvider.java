package com.algomeet.notificationservice.websocket.processor;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.algomeet.notificationservice.enums.MessageType;

@Component
public class WebSocketMessageProcessorProvider {
	@Autowired
	private ApplicationContext applicationContext;	
	
	private Map<MessageType, WebSocketMessageProcessor> map;

	public Map<MessageType, WebSocketMessageProcessor> getProcessors() {
		if (map != null) {
			return map;
		}
		
		synchronized (this) {
			Map<String, WebSocketMessageProcessor> processorMap = applicationContext.getBeansOfType(WebSocketMessageProcessor.class);
			return processorMap.values().stream()		
			 .collect(Collectors.toMap(
					 WebSocketMessageProcessor::getMessageType, 
					 processor -> processor
				    ));
		}
	}
}

