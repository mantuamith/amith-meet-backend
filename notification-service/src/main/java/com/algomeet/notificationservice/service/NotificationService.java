package com.algomeet.notificationservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.model.Notification;
import com.algomeet.notificationservice.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification create(Notification notification) {
        notification.setCreatedAt(Instant.now());
        return notificationRepository.saveAndFlush(notification);
    }

    public Optional<Notification> getById(UUID id) {
        return notificationRepository.findById(id);
    }

    public List<Notification> getByReceiverId(String receiverId) {
        return notificationRepository.findByReceiverId(receiverId);
    }

    public void delete(UUID id) {
        notificationRepository.deleteById(id);
    }
}
