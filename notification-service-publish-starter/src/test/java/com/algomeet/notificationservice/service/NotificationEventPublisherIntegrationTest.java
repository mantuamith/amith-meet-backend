package com.algomeet.notificationservice.service;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.algomeet.notificationservice.NotificationServicePublishStarterApplication;
import com.algomeet.notificationservice.config.NotificationServicePublishStarterAutoConfiguration;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;


@SpringBootTest(classes = NotificationServicePublishStarterApplication.class)
@Import(NotificationServicePublishStarterAutoConfiguration.class)
public class NotificationEventPublisherIntegrationTest {
    @Autowired
    private NotificationService notificationService;

    @Test   
    public void testPublishEvent()  {
    	Notification notification = new Notification();
    	notification.setType(NotificationType.MESSAGE);
    	notification.setReceiverIds(Set.of("maddox"));
    	notificationService.sendPush(notification);
    }
}