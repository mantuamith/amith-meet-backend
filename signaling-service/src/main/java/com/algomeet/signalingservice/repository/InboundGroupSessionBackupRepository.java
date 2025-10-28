package com.algomeet.signalingservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.entity.InboundGroupSessionBackupId;
import com.algomeet.signalingservice.entity.InboundGroupSessionBackup;

public interface InboundGroupSessionBackupRepository extends JpaRepository<InboundGroupSessionBackup, InboundGroupSessionBackupId> {
	@Transactional(readOnly = true)
	@Query("""
        SELECT m FROM InboundGroupSessionBackup m 
        WHERE m.id.userKey = :userKey AND 
        m.id.sessionId = :sessionId AND m.id.ratchetIndex <= :targetIndex
        ORDER BY m.id.ratchetIndex DESC
        LIMIT 1
    """)
    Optional<InboundGroupSessionBackup> findClosestBackup(UUID userKey, String sessionId, int targetIndex);
    
	@Transactional(readOnly = true)
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
    
	@Transactional(readOnly = true)
    List<InboundGroupSessionBackup> findById_UserKey(UUID userKey);
}