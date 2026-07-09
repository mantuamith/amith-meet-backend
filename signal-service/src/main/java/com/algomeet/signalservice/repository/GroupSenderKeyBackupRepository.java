package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.algomeet.signalservice.entity.GroupSenderKeyBackup;
import com.algomeet.signalservice.entity.GroupSenderKeyBackupId;

import jakarta.transaction.Transactional;

public interface GroupSenderKeyBackupRepository extends JpaRepository<GroupSenderKeyBackup, GroupSenderKeyBackupId> {
    List<GroupSenderKeyBackup> findByIdUserKey(UUID userKey);
    List<GroupSenderKeyBackup> findByIdGroupId(UUID groupId);
    
    @Modifying
	@Transactional
    void deleteByIdGroupId(UUID groupId);
}