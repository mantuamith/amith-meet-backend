package com.algomeet.notificationservice.dto;

import java.time.Instant;

import lombok.Data;

@Data
public class NotificationAckDto {

    private String type;
    private long notificationId;
    private Status status;
    private Instant acknowledgedAt;
    private Metadata metadata;

    public enum Status {
        DELIVERED,
        READ,
        FAILED,
        DISMISSED
    }

    @Data
    public static class Metadata {
        private String device;
        private String appVersion;
        private String deliveryChannel;

        public Metadata() {}

        public Metadata(String device, String appVersion, String deliveryChannel) {
            this.device = device;
            this.appVersion = appVersion;
            this.deliveryChannel = deliveryChannel;
        }
    }

    public NotificationAckDto() {}

    public NotificationAckDto(String type, long notificationId, String receiverId,
                              Status status, Instant acknowledgedAt, Metadata metadata) {
        this.type = type;
        this.notificationId = notificationId;
        this.status = status;
        this.acknowledgedAt = acknowledgedAt;
        this.metadata = metadata;
    }
}