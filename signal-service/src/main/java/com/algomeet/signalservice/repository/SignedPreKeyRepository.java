package com.algomeet.signalservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.entity.SignedPreKey;
import com.algomeet.signalservice.entity.SignedPreKeyId;

@Repository
public interface SignedPreKeyRepository extends JpaRepository<SignedPreKey, SignedPreKeyId> {
    // Add custom queries if needed
}
