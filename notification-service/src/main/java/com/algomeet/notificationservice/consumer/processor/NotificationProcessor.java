package com.algomeet.notificationservice.consumer.processor;

import com.algomeet.notificationservice.dto.NotificationDto;

public interface NotificationProcessor {
	default int getOrder() {
		return 1;
	}
	
	void doProcess(NotificationDto notificationDto);
}
