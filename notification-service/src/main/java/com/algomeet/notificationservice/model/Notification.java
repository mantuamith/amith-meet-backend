package com.algomeet.notificationservice.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Column(length = 24)
    private String type;
    @Column(length = 255)
    private String title;

    @Column(length = 2000)
    private String body;

    @Column(length = 32)
    private String senderId;
    
    @Column(length = 2000)
    private String receiverId;
    
    @Column(length = 32)
    private String receiverGroup;
    
    @Column(length = 32)
    private String receiverGroupRefId;

    private Instant createdAt;
    private Instant expiredAt;
    private boolean deliveryAckRequired;
    private Instant updatedAt;

    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "TEXT", length = 4000)
    private Map<String, Object> data;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserNotification> userNotifications = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}