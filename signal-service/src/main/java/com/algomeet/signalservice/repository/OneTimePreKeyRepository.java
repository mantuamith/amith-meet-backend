package com.algomeet.signalservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.algomeet.signalservice.entity.OneTimePreKey;
import java.util.List;
import java.util.UUID;


public interface OneTimePreKeyRepository extends JpaRepository<OneTimePreKey, Long> {
	List<OneTimePreKey> findByUserKeyAndDeviceIdAndUsedFalse(UUID userKey, Integer deviceId);
	
	OneTimePreKey findFirstByUserKeyAndDeviceIdAndUsedFalseOrderByIdAsc(UUID userKey, Integer deviceId);
	
	long countByUserKeyAndDeviceIdAndUsedFalse(UUID userKey, Integer deviceId);	
	
	@Modifying
	void deleteByUserKey(UUID userKey);
	
	@Modifying
	void deleteByUserKeyAndDeviceId(UUID userKey, Integer deviceId);
}