package com.algomeet.signalingservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.algomeet.signalingservice.document.MessageBackupDocument;

public interface MessageBackupRepository extends MongoRepository<MessageBackupDocument, String> {
    Page<MessageBackupDocument> findByUserKeyAndSenderKeyOrderByTimestampDesc(String userKey, String senderKey, Pageable pageable);
    
    void deleteByUserKeyAndSenderKey(String userKey, String senderKey);
    
    void deleteByUserKey(String userKey);
}
