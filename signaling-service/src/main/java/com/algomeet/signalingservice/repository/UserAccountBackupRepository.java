package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.UserAccountBackup;
import com.algomeet.signalingservice.entity.UserAccountBackupId;

import jakarta.transaction.Transactional;

@Repository
public interface UserAccountBackupRepository extends JpaRepository<UserAccountBackup, UserAccountBackupId> {
    List<UserAccountBackup> findByIdUserKey(UUID userKey);
    
    @Modifying
    @Transactional
    void deleteByIdUserKey(UUID userKey);
}    