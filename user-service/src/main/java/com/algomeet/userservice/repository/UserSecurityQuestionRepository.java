package com.algomeet.userservice.repository;

import com.algomeet.userservice.model.UserSecurityQuestion;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSecurityQuestionRepository extends JpaRepository<UserSecurityQuestion, Integer> {
    List<UserSecurityQuestion> findByUserProfileId(UUID userProfileId);
    
    @Transactional
    void deleteByUserProfileId(UUID userProfileId);
    
    Optional<UserSecurityQuestion> findByUserProfileIdAndSecurityQuestion_Id(UUID userProfileId, String securityQuestionId);

}