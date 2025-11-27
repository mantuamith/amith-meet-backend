package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.entity.GroupSessionBackup;
import com.algomeet.signalservice.entity.GroupSessionBackupId;


public interface GroupSessionBackupRepository extends JpaRepository<GroupSessionBackup, GroupSessionBackupId> {
	List<GroupSessionBackup> findByIdUserKey(UUID userKey);
	
	List<GroupSessionBackup> findByIdUserKeyAndIdGroupIdAndIdDistributionId(UUID userKey, String groupId, UUID distributionId);
	
	List<GroupSessionBackup> findByIdUserKeyAndDeviceId(UUID userKey, Integer deviceId);
	
	@Modifying
    @Transactional
	void deleteByIdUserKeyAndIdGroupIdAndIdDistributionId(UUID userKey, String groupId, UUID distributionId);
	
	@Modifying
    @Transactional
	void deleteByIdUserKeyAndDeviceId(UUID userKey, Integer deviceId);
	
    @Modifying
    @Transactional
	void deleteByIdUserKey(UUID userKey);
}