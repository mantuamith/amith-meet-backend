package com.algomeet.notificationservice.consumer.receiver.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ReceiverGroupProcessorProvider {
	@Autowired
	private ApplicationContext applicationContext;	
	
	private List<ReceiverGroupProcessor> list;

	public List<ReceiverGroupProcessor> getProcessors() {
		if (list != null) {
			return list;
		}
		
		synchronized (this) {
			Map<String, ReceiverGroupProcessor> processorrMap = applicationContext.getBeansOfType(ReceiverGroupProcessor.class);
			List<ReceiverGroupProcessor> list = new ArrayList<ReceiverGroupProcessor>(processorrMap.values());
			this.list = list;
			
			return list;
		}
	}
}

