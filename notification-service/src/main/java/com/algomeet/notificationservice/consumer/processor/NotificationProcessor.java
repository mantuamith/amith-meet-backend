package com.algomeet.notificationservice.consumer.processor;

import com.algomeet.notificationservice.dto.NotificationDto;

public interface NotificationProcessor {
	/**
	 * It is used to determine the order of execution, it's very important 
	 * since some of the process might dependent to other process.
	 * @return
	 */
	default int getOrder() {
		return 1;
	}
	
	void doProcess(NotificationDto notificationDto);
}
