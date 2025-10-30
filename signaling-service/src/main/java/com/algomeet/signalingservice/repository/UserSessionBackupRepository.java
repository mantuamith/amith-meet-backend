package com.algomeet.signalingservice.repository;


import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.entity.UserSessionBackup;
import com.algomeet.signalingservice.entity.UserSessionBackupId;

public interface UserSessionBackupRepository extends JpaRepository<UserSessionBackup, UserSessionBackupId> {
	@Transactional(readOnly = true)
    List<UserSessionBackup> findByUserKey(UUID userKey);
}