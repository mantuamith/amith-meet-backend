package com.algomeet.notificationservice.events;


import org.springframework.context.ApplicationEvent;

import com.algomeet.notificationservice.dto.Notification;

public class NotificationCreatedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;
	private final Notification notification;

    public NotificationCreatedEvent(Object source, Notification notification) {
        super(source);
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }
}