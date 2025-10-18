package com.algomeet.signalingservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.UserKeysBackup;

@Repository
public interface UserKeysBackupRepository extends JpaRepository<UserKeysBackup, UUID> {    
    Optional<UserKeysBackup> findByUserKey(UUID userKey);
}