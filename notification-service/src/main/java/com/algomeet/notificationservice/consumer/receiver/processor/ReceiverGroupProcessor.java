package com.algomeet.notificationservice.consumer.receiver.processor;

import java.util.List;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.UserDto;

public interface ReceiverGroupProcessor {	
	List<UserDto> getUserList(NotificationDto notificationDto);
}
