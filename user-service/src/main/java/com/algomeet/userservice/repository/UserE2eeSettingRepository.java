package com.algomeet.userservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.userservice.model.UserE2eeSetting;

@Repository
public interface UserE2eeSettingRepository extends JpaRepository<UserE2eeSetting, UUID> {
    // Optional: add custom queries if needed
}
