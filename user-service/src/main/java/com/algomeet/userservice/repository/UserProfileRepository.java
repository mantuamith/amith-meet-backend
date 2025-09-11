package com.algomeet.userservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.algomeet.userservice.model.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}