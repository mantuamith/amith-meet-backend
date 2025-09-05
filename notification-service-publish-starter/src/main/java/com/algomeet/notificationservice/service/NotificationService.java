package com.algomeet.notificationservice.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.event.NotificationCreatedEvent;

import jakarta.validation.ValidationException;

public class NotificationService {	
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public void sendPush(Notification notification) {
    	if(CollectionUtils.isEmpty(notification.getReceiverIds())
    			&& Objects.isNull(notification.getReceiverGroup())) {
    		throw new ValidationException("Receiver has not set.");  
    	}
    	
    	if(Objects.isNull(notification.getId())) {
    		notification.setId(UUID.randomUUID());
    	}
    	
    	if (Objects.isNull(notification.getCreatedAt())) {
    		notification.setCreatedAt(Instant.now());
    	}
    	
    	// Set tenant id manually
    	if (Objects.isNull(notification.getTenantId())) {
    		notification.setTenantId(TenantContext.getCurrentTenant());
    	}
	    
		// Publish event
		try {
			eventPublisher.publishEvent(new NotificationCreatedEvent(this, notification));
		} catch (Exception ex) {
			throw ex;
		}
    } 
}
