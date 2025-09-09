package com.algomeet.notificationservice.consumer.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class NotificationProcessorProvider {
	@Autowired
	private ApplicationContext applicationContext;	
	
	private List<NotificationProcessor> list;

	public List<NotificationProcessor> getProcessors() {
		if (list != null) {
			return list;
		}
		
		synchronized (this) {
			Map<String, NotificationProcessor> processorrMap = applicationContext.getBeansOfType(NotificationProcessor.class);
			List<NotificationProcessor> list = new ArrayList<NotificationProcessor>(processorrMap.values());
			Collections.sort(list, Comparator.comparingInt(NotificationProcessor::getOrder));
			this.list = list;
			
			return list;
		}
	}
}

