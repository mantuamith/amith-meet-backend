package com.algomeet.signalservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.algomeet.signalservice.document.MessageBackupDocument;

import jakarta.transaction.Transactional;

public interface MessageBackupRepository extends MongoRepository<MessageBackupDocument, String> {
    @Query("{ '$or': [ " +
            "{ 'userKey': ?0, 'senderKey': ?0, 'receiverKey': ?1 }, " +
            "{ 'userKey': ?0, 'senderKey': ?1, 'receiverKey': ?0 } " +
            "] }")
    Page<MessageBackupDocument> findConversation(String userA, String userB, Pageable pageable);

    // Custom delete query for both sides of conversation
    @Modifying
    @Query(value = "{ '$or': [ " +
            "{ 'userKey': ?0, 'senderKey': ?0, 'receiverKey': ?1 }, " +
            "{ 'userKey': ?0, 'senderKey': ?1, 'receiverKey': ?0 } " +
            "] }",
           delete = true)
    @Transactional
    void deleteConversation(String userA, String userB);
    
    @Modifying
    @Transactional
    void deleteByUserKey(String userKey);
}
