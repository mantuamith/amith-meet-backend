package com.algomeet.notificationservice.util;

import java.util.HashMap;
import java.util.Objects;

import com.algomeet.notificationservice.constant.Constants;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.model.UserNotification;

public class UserNotificationUtil {

	public static void addNotificationCustomData(NotificationDto dto, UserNotification userNotification) {	
		if (Objects.isNull(dto.getData())) {
			dto.setData(new HashMap<>());
		}

		if (Objects.nonNull(userNotification)) {
			dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_NOTIFICATION_ID, userNotification.getId());
		}

		dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_NOTIFICATION_TYPE, dto.getType());
		dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_DELIVERY_ACK_REQUIRED, dto.isDeliveryAckRequired());

	}
	
	public static void addNotificationCustomData(NotificationDto dto, UserNotificationDto userNotificationDto) {	
		if (Objects.isNull(dto.getData())) {
			dto.setData(new HashMap<>());
		}

		if (Objects.nonNull(userNotificationDto)) {
			dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_NOTIFICATION_ID, userNotificationDto.getId());
		}

		dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_NOTIFICATION_TYPE, dto.getType());
		dto.getData().put(Constants.NOTIFICATION_CUSTOM_DATA_DELIVERY_ACK_REQUIRED, dto.isDeliveryAckRequired());

	}
}
