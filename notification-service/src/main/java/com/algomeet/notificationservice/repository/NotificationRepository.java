package com.algomeet.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.algomeet.notificationservice.model.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByReceiverId(String receiverId);

    List<Notification> findBySenderId(String senderId);
}