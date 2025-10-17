package com.algomeet.signalingservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.UserPrivateKeyBackup;

@Repository
public interface UserPrivateKeyBackupRepository extends JpaRepository<UserPrivateKeyBackup, UUID> {    
    Optional<UserPrivateKeyBackup> findByUserKey(UUID userKey);
}