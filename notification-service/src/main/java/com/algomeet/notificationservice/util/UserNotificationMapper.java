package com.algomeet.notificationservice.util;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.model.Notification;
import com.algomeet.notificationservice.model.UserNotification;

public class UserNotificationMapper {

    public static NotificationDto toNotificationDto(Notification entity) {
        if (entity == null) return null;
        return NotificationDto.builder()
                .id(entity.getId())
                .type(entity.getType())
                .title(entity.getTitle())
                .body(entity.getBody())
                .senderId(entity.getSenderId())
                .createdAt(entity.getCreatedAt())
                .expiredAt(entity.getExpiredAt())
                .deliveryAckRequired(entity.isDeliveryAckRequired())
                .updatedAt(entity.getUpdatedAt())
                .data(entity.getData())
                .build();
    }

    public static UserNotificationDto toUserNotificationDto(UserNotification entity) {
        if (entity == null) return null;
        return UserNotificationDto.builder()
                .notification(toNotificationDto(entity.getNotification()))
                .userId(entity.getUserId())
                .read(entity.isRead())
                .delivered(entity.isDelivered())
                .updatedAt(entity.getUpdatedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
