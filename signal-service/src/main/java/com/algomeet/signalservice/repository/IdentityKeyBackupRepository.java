package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.IdentityKeyBackup;
import com.algomeet.signalservice.entity.IdentityKeyBackupId;

import jakarta.transaction.Transactional;

@Repository
public interface IdentityKeyBackupRepository extends JpaRepository<IdentityKeyBackup, IdentityKeyBackupId> {
    List<IdentityKeyBackup> findByIdUserKey(UUID userKey);
    
    @Modifying
    @Transactional
    void deleteByIdUserKey(UUID userKey);
}    