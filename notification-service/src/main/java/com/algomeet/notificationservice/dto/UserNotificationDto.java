package com.algomeet.notificationservice.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationDto {
    private NotificationDto notification;
    private String userKey;
    private boolean read;
    private boolean delivered;
    private Instant updatedAt;
    private Instant createdAt;
}