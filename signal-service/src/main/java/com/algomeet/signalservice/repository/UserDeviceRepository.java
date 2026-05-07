package com.algomeet.signalservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.UserDevice;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.view.UserDeviceView;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UserDeviceId> {	
    List<UserDeviceView> findByIdUserKeyIn(Collection<UUID> userKeys);

	// Get the maximum deviceId for a given userKey
	@Query("SELECT MAX(ud.id.deviceId) FROM UserDevice ud WHERE ud.id.userKey = :userKey")
	Optional<Integer> findMaxDeviceIdByUserKey(@Param("userKey") UUID userKey);

	/**
     * Finds specific UserDevices by deviceId list and eagerly fetches keys using an Entity Graph.
     * The 'attributePaths' defines which 1:1 relationships to JOIN FETCH.
     */
	@EntityGraph(attributePaths = {"signedPreKey", "kyberPreKey"})
	List<UserDevice> findByIdUserKey(UUID userKey);

	/**
     * Finds specific UserDevices by deviceId list and eagerly fetches keys using an Entity Graph.
     * The 'attributePaths' defines which 1:1 relationships to JOIN FETCH.
     */
    @EntityGraph(attributePaths = {"signedPreKey", "kyberPreKey"})
    List<UserDevice> findByIdUserKeyAndIdDeviceIdIn(UUID userKey, Collection<Integer> deviceIds);
}