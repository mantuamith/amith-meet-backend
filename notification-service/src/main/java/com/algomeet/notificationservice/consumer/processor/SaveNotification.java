package com.algomeet.notificationservice.consumer.processor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.model.Notification;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.notificationservice.util.NotificationMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SaveNotification implements NotificationProcessor{
	@Value("${redis.notification.defaultExpirationInDays:30}")
	private int notificationDefaultExpirationInDays;
	
	@Autowired
	private NotificationService notificationService;
	
	@Override
	public int getOrder() {		
		return 0;
	}
		
	@Override
	public void doProcess(NotificationDto notificationDto) {
		try {
			if(!(notificationDto.isDeliveryAckRequired())) {
				// Not need to save the notification
				return;
			}
			
			Notification notification = NotificationMapper.toEntity(notificationDto);
			
			if (Objects.isNull(notification.getExpiredAt())) {
				Instant now = Instant.now();
		        Instant expiration = now.plus(notificationDefaultExpirationInDays, ChronoUnit.DAYS);
		        
				notification.setExpiredAt(expiration);
			}
			
			if (notificationDto.getTenantId() == null) {
				log.info("Notification Tenant Id has null value");
			}
			
			notificationService.create(notification);	
		} catch(Exception ex) {
			log.error("Error saving notification {}", ex.getMessage(), ex);
		}		
	}
}
