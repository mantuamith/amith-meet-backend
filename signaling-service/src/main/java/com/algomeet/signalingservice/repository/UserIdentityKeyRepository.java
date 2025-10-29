package com.algomeet.signalingservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.UserIdentityKey;

import jakarta.transaction.Transactional;

@Repository
public interface UserIdentityKeyRepository extends JpaRepository<UserIdentityKey, UUID> {
    boolean existsByIdentityKey(String identityKey);
    
    Optional<UserIdentityKey> findByUserKey(UUID userKey);
    
    Optional<UserIdentityKey> findByIdentityKey(String identityKey);
    
    @Modifying
    @Transactional
    void deleteByUserKey(UUID userKey);
}