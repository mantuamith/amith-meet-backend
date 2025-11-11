package com.algomeet.userservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.userservice.model.E2eeUserSetting;

@Repository
public interface E2eeUserSettingRepository extends JpaRepository<E2eeUserSetting, UUID> {
    // Optional: add custom queries if needed
}
