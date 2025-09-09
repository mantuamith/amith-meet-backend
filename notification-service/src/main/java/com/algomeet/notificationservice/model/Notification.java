package com.algomeet.notificationservice.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @Column(nullable = false)
    private UUID id;

    private String type;
    private String title;

    @Column(length = 2000)
    private String body;

    private String senderId;
    private String receiverId;
    private String receiverGroup;
    private String receiverGroupRefId;

    private Instant createdAt;
    private Instant expiredAt;
    private boolean deliveryAckRequired;
    private Instant updatedAt;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> data;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<UserNotification> userNotifications = new java.util.ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}