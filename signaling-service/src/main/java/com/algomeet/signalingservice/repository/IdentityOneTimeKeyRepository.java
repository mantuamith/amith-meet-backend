package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.algomeet.signalingservice.entity.IdentityOneTimeKey;

import jakarta.transaction.Transactional;

@Repository
public interface IdentityOneTimeKeyRepository extends JpaRepository<IdentityOneTimeKey, Long> {
    List<IdentityOneTimeKey> findByUserKey(UUID userKey);
    Optional<IdentityOneTimeKey> findFirstByUserKeyAndUsedFalse(UUID userKey);
    List<IdentityOneTimeKey> findByUserKeyAndOneTimeKeyIn(UUID userKey, List<String> oneTimeKeys);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM IdentityOneTimeKey i WHERE i.id = :id AND (i.userKey = :userKey OR i.used = :used)")
    void deleteByIdAndUserKeyOrUsed(@Param("id") Long id,
                                    @Param("userKey") UUID userKey,
                                    @Param("used") boolean used);
}