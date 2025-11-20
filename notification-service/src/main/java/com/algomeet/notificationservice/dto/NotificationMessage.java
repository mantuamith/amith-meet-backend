package com.algomeet.notificationservice.dto;

import java.util.Map;

import com.algomeet.notificationservice.enums.MessageType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class NotificationMessage extends ExchangeMessage{
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

    @Override
	public MessageType getType() {
		return MessageType.NOTIFICATION;
	}
}