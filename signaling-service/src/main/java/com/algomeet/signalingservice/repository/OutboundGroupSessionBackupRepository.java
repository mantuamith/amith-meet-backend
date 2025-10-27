package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.signalingservice.entity.OutboundGroupSessionBackup;
import com.algomeet.signalingservice.entity.OutboundGroupSessionBackupId;

public interface OutboundGroupSessionBackupRepository extends JpaRepository<OutboundGroupSessionBackup, OutboundGroupSessionBackupId> {
	List<OutboundGroupSessionBackup> findByUserKey(UUID userKey);
}