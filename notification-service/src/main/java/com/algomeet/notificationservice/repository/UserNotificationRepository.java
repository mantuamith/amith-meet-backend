package com.algomeet.notificationservice.repository;

import com.algomeet.notificationservice.model.UserNotification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

	Optional<UserNotification> findByUserKey(UUID userKey);
	
	Page<UserNotification> findByUserKey(UUID userKey, Pageable pageable);
	
	Page<UserNotification> findByUserKeyAndReadFalse(UUID userKey, Pageable pageable);

    List<UserNotification> findByUserKeyAndReadFalse(UUID userKey);

    List<UserNotification> findByNotification_Id(UUID notificationId);
    
    List<UserNotification> findByUserKeyAndNotification_Id(UUID userKey, UUID notificationId);
}