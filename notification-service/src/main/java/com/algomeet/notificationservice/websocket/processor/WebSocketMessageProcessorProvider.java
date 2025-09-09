package com.algomeet.notificationservice.websocket.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class WebSocketMessageProcessorProvider {
	@Autowired
	private ApplicationContext applicationContext;	
	
	private List<WebSocketMessageProcessor> list;

	public List<WebSocketMessageProcessor> getProcessors() {
		if (list != null) {
			return list;
		}
		
		synchronized (this) {
			Map<String, WebSocketMessageProcessor> processorMap = applicationContext.getBeansOfType(WebSocketMessageProcessor.class);
			List<WebSocketMessageProcessor> list = new ArrayList<WebSocketMessageProcessor>(processorMap.values());
			Collections.sort(list, Comparator.comparingInt(WebSocketMessageProcessor::getOrder));
			this.list = list;
			
			return list;
		}
	}
}

