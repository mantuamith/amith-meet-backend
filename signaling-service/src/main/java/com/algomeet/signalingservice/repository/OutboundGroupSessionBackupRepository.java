package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.entity.OutboundGroupSessionBackup;
import com.algomeet.signalingservice.entity.OutboundGroupSessionBackupId;

public interface OutboundGroupSessionBackupRepository extends JpaRepository<OutboundGroupSessionBackup, OutboundGroupSessionBackupId> {
	@Transactional(readOnly = true)
	List<OutboundGroupSessionBackup> findById_UserKey(UUID userKey);
	
	/** Count all outbound group session backups for a specific user key */
    @Transactional(readOnly = true)
    long countById_UserKey(UUID userKey);
        
    @Modifying
    @Transactional
	void deleteByIdUserKey(UUID userKey);
}