package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UserDeviceId> {
	List<UserDevice> findByIdUserKey(UUID userKey);

	// Get the maximum deviceId for a given userKey
	@Query("SELECT MAX(ud.id.deviceId) FROM UserDevice ud WHERE ud.id.userKey = :userKey")
	Optional<Integer> findMaxDeviceIdByUserKey(UUID userKey);
}