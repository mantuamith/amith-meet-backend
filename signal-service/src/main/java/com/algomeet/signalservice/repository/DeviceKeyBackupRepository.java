package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.DeviceKeyBackup;
import com.algomeet.signalservice.entity.DeviceKeyBackupId;

import jakarta.transaction.Transactional;

@Repository
public interface DeviceKeyBackupRepository extends JpaRepository<DeviceKeyBackup, DeviceKeyBackupId> {
    List<DeviceKeyBackup> findByIdUserKey(UUID userKey);
    
    @Modifying
    @Transactional
    void deleteByIdUserKey(UUID userKey);
}    