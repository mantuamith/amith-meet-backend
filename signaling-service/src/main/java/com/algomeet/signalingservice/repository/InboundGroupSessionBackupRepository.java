package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.algomeet.signalingservice.entity.InboundGroupSessionBackupId;
import com.algomeet.signalingservice.entity.InboundGroupSessionBackup;

public interface InboundGroupSessionBackupRepository extends JpaRepository<InboundGroupSessionBackup, InboundGroupSessionBackupId> {
    @Query("""
        SELECT m FROM MegolmSessionBackup m 
        WHERE m.userKey = :userKey
        m.sessionId = :sessionId AND m.ratchetIndex <= :targetIndex
        ORDER BY m.ratchetIndex DESC
        LIMIT 1
    """)
    Optional<InboundGroupSessionBackup> findClosestBackup(UUID userKey, String sessionId, int targetIndex);
    
    /**  Find all backups for user ordered by ratchetIndex ASC */
    List<InboundGroupSessionBackup> findByUserKeyOrderByRatchetIndexAsc(UUID userKey);
    
    @Query("""
    	    SELECT b FROM InboundGroupSessionBackup b
    	    WHERE b.id.userKey = :userKey
    	      AND b.id.ratchetIndex = (
    	          SELECT MAX(sub.id.ratchetIndex)
    	          FROM InboundGroupSessionBackup sub
    	          WHERE sub.id.userKey = b.id.userKey
    	            AND sub.id.sessionId = b.id.sessionId
    	      )
    	    ORDER BY b.id.sessionId ASC
    	""")
    List<InboundGroupSessionBackup> findHighestRatchetIndexByUserKeyGroupedBySessionId(UUID userKey);
    
    List<InboundGroupSessionBackup> findByUserKey(UUID userKey);
}