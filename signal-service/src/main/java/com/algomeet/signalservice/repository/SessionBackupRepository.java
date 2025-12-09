package com.algomeet.signalservice.repository;


import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.entity.SessionBackup;
import com.algomeet.signalservice.entity.SessionBackupId;


public interface SessionBackupRepository extends JpaRepository<SessionBackup, SessionBackupId> {
	@Transactional(readOnly = true)
    List<SessionBackup> findByIdUserKeyAndIdDeviceId(UUID userKey, Integer deviceId);
	
    @Modifying
    @Transactional
	void deleteByIdUserKey(UUID userKey);
    
    @Modifying
    @Transactional
	void deleteByIdUserKeyAndIdDeviceId(UUID userKey, Integer deviceId);
}