package com.algomeet.notificationservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.repository.UserNotificationRepository;
import com.algomeet.notificationservice.util.UserNotificationMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserNotificationService {

    private final UserNotificationRepository userNotificationRepository;
  
    public Page<UserNotificationDto> getUserNotifications(Long userId, int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return userNotificationRepository.findByUserId(userId, pageable)
                .map(UserNotificationMapper::toUserNotificationDto);
    }
    
    public Page<UserNotificationDto> getUnreadNotifications(Long userId, int page, int size, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return userNotificationRepository.findByUserIdAndReadFalse(userId, pageable)
                .map(UserNotificationMapper::toUserNotificationDto);
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