package com.algomeet.mediaservice.repository;

import com.algomeet.mediaservice.document.UserFileDocument;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserFileRepository extends MongoRepository<UserFileDocument, String> {
    // Owner access
    List<UserFileDocument> findByOwner(String owner);

    // Single file with ownership or ACL access
    @Query("""
        {
          "_id": ?0,
          "$or": [
            { "owner": ?1 },
            { "access_control_list.userId": ?1 }
          ]
        }
        """)
    Optional<UserFileDocument> findAccessibleFile(String fileId, String userKey);
    
    @Query("{ 'cleanupEligibleAt': { $lte: ?0 } }")
    List<UserFileDocument> findCleanupEligible(Instant now, Pageable pageable);

    long deleteByCleanupEligibleAtLessThanEqual(Instant now);
}
