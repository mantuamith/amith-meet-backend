package com.algomeet.signalservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UserDeviceId> {
	@Query("""
		    SELECT ud FROM UserDevice ud
		    WHERE ud.id.userKey IN :userKeys
		""")
	List<UserDevice> findByIdUserKeyIn(@Param("userKeys") List<UUID> userKeys);

	// Get the maximum deviceId for a given userKey
	@Query("SELECT MAX(ud.id.deviceId) FROM UserDevice ud WHERE ud.id.userKey = :userKey")
	Optional<Integer> findMaxDeviceIdByUserKey(@Param("userKey") UUID userKey);

	/**
	 * Finds all UserDevices for a user and eagerly fetches SignedPreKey and KyberPreKey.
	 * This avoids N+1 queries when fetching all devices for a user.
	 */
	@Query("SELECT DISTINCT ud FROM UserDevice ud "
			+ "LEFT JOIN FETCH ud.signedPreKey spk "
			+ "LEFT JOIN FETCH ud.kyberPreKey kpk "
			+ "WHERE ud.id.userKey = :userKey")
	List<UserDevice> findAllByUserKeyWithKeys(@Param("userKey") UUID userKey);

	/**
	 * Finds specific UserDevices by deviceId list and eagerly fetches keys.
	 * This avoids N+1 queries when fetching a subset of devices.
	 */
	@Query("SELECT ud FROM UserDevice ud "
			+ "LEFT JOIN FETCH ud.signedPreKey spk "
			+ "LEFT JOIN FETCH ud.kyberPreKey kpk "
			+ "WHERE ud.id.userKey = :userKey AND ud.id.deviceId IN :deviceIds")
	List<UserDevice> findAllByUserKeyAndDeviceIdsWithKeys(@Param("userKey") UUID userKey, 
			@Param("deviceIds") Collection<Integer> deviceIds);
}