package com.algomeet.signalingservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.IdentityOneTimeKey;

@Repository
public interface IdentityOneTimeKeyRepository extends JpaRepository<IdentityOneTimeKey, Long> {
    List<IdentityOneTimeKey> findByIdentityKey(String identityKey);
    List<IdentityOneTimeKey> findByIdentityKeyAndUsedFalse(String identityKey);
    
}