package com.algomeet.notificationservice.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class NotificationMessage {
    private String title;
    private String body;

    private Map<String, Object> data;
    
    public static NotificationMessage getNotificationMessage(NotificationDto dto) {
    	NotificationMessage notif = new NotificationMessage();
    	notif.setBody(dto.getBody());
    	notif.setTitle(dto.getTitle());
    	notif.setData(dto.getData());
    	
    	return notif;
    }
}