package com.algomeet.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishPushMessageDto {
	private String userKey;
	private NotificationMessage notificationMessage;
}
