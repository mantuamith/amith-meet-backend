package com.algomeet.notificationservice.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import com.algomeet.notificationservice.events.NotificationCreatedEvent;
import com.algomeet.notificationservice.publisher.NotificationStreamPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class NotificationEventListener {	
	@Autowired
	private NotificationStreamPublisher notificationPublisher;
	@Autowired
	private ObjectMapper objectMapper;
	
    @EventListener
    public void handleNotificationCreated(NotificationCreatedEvent event) throws JsonProcessingException {
		ObjectWriter ow = objectMapper.writer().withDefaultPrettyPrinter();
		String jsonMessage = ow.writeValueAsString(event.getNotification());
        notificationPublisher.publish(jsonMessage);
    }
}