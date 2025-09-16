package com.algomeet.notificationservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.repository.UserNotificationRepository;
import com.algomeet.notificationservice.util.UserNotificationMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final UserNotificationRepository userNotificationRepository;
  
    public Page<UserNotificationDto> getUserNotifications(String userKey, int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return userNotificationRepository.findByUserKey(UUID.fromString(userKey), pageable)
                .map(UserNotificationMapper::toUserNotificationDto);
    }
    
    public Page<UserNotificationDto> getUnreadNotifications(String userKey, int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return userNotificationRepository.findByUserKeyAndReadFalse(UUID.fromString(userKey), pageable)
                .map(UserNotificationMapper::toUserNotificationDto);
    }
    
    @Transactional
    public List<UserNotificationDto> getUndeliveredNotifications(String userKey) {
        return userNotificationRepository.findByUserKeyAndDeliveredFalse(UUID.fromString(userKey))
        		.stream()
                .map(UserNotificationMapper::toUserNotificationDto)
                .toList();
    }
         
    public void markAsRead(Long id) {
        userNotificationRepository.findById(id)
            .ifPresent(un -> {
                un.setRead(true);
                // Make as deliver also since when you able to read the message
                un.setDelivered(true);
                userNotificationRepository.save(un);
            });
    }
    
    public void markAsDelivered(Long id) {
        userNotificationRepository.findById(id)
            .ifPresent(un -> {
                un.setDelivered(true);
                userNotificationRepository.save(un);
            });
    }

    public void deleteUserNotification(Long id) {
        userNotificationRepository.deleteById(id);
    }
}