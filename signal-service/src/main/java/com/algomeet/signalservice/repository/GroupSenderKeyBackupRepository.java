package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.signalservice.entity.GroupSenderKeyBackup;
import com.algomeet.signalservice.entity.GroupSenderKeyBackupId;

public interface GroupSenderKeyBackupRepository extends JpaRepository<GroupSenderKeyBackup, GroupSenderKeyBackupId> {
    List<GroupSenderKeyBackup> findByIdUserKey(UUID userKey);
    List<GroupSenderKeyBackup> findByIdGroupId(String groupId);
}