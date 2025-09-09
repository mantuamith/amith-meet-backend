package com.algomeet.notificationservice.repository;

import com.algomeet.notificationservice.model.UserNotification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

	Page<UserNotification> findByUserId(Long userId, Pageable pageable);
	
	Page<UserNotification> findByUserIdAndReadFalse(Long userId, Pageable pageable);

    List<UserNotification> findByUserIdAndReadFalse(Long userId);

    List<UserNotification> findByNotification_Id(UUID notificationId);
    
    List<UserNotification> findByUserIdAndNotification_Id(Long userId, UUID notificationId);
}