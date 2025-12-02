package com.algomeet.signalservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.algomeet.signalservice.entity.OneTimePreKey;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


public interface OneTimePreKeyRepository extends JpaRepository<OneTimePreKey, Long> {
	List<OneTimePreKey> findByUserKeyAndDeviceIdAndUsedFalse(UUID userKey, Integer deviceId);

	OneTimePreKey findFirstByUserKeyAndDeviceIdAndUsedFalseOrderByIdAsc(UUID userKey, Integer deviceId);

	List<OneTimePreKey> findByUserKeyAndDeviceId(UUID userKey, Integer deviceId);
	
	/**
     * Finds the single oldest (lowest ID) unused One-Time PreKey for each device 
     * specified in the collection for a given user. This is crucial for batch retrieval.
     */
    @Query("SELECT opk FROM OneTimePreKey opk "
        + "WHERE opk.userKey = :userKey "
        + "AND opk.deviceId IN :deviceIds "
        + "AND opk.used = FALSE "
        + "AND opk.id = ("
            + "SELECT MIN(sub.id) FROM OneTimePreKey sub "
            + "WHERE sub.userKey = opk.userKey "
            + "AND sub.deviceId = opk.deviceId "
            + "AND sub.used = FALSE"
        + ")")
    List<OneTimePreKey> findFirstUnusedPreKeysByUserKeyAndDeviceIds(
        @Param("userKey") UUID userKey, 
        @Param("deviceIds") Collection<Integer> deviceIds);
	
	long countByUserKeyAndDeviceIdAndUsedFalse(UUID userKey, Integer deviceId);	
	
	@Modifying
	@Transactional
	void deleteByUserKey(UUID userKey);

	@Modifying
	@Transactional
	void deleteByUserKeyAndDeviceId(UUID userKey, Integer deviceId);
	
	/**
     * Deletes One-Time PreKeys in a single bulk operation by their IDs.
     * Use this after successfully retrieving the keys via findFirstUnusedPreKeysByUserKeyAndDeviceIds.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM OneTimePreKey opk WHERE opk.id IN :ids")
    void deleteByIdInBatch(@Param("ids") Collection<Long> ids);
}