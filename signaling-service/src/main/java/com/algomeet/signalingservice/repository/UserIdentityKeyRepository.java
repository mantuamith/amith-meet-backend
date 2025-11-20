package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.UserIdentityKey;
import com.algomeet.signalingservice.entity.UserIdentityKeyId;

import jakarta.transaction.Transactional;

@Repository
public interface UserIdentityKeyRepository extends JpaRepository<UserIdentityKey, UserIdentityKeyId> {   
    List<UserIdentityKey> findByIdUserKey(UUID userKey);
    
    Optional<UserIdentityKey> findByIdUserKeyAndDeviceId(UUID userKey, String deviceId);
    
    @Modifying
    @Transactional
    void deleteByIdUserKey(UUID userKey);
}