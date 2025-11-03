package com.algomeet.signalingservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.UserAccountBackup;

@Repository
public interface UserAccountBackupRepository extends JpaRepository<UserAccountBackup, UUID> {
    Optional<UserAccountBackup> findByUserKey(UUID userKey);
}