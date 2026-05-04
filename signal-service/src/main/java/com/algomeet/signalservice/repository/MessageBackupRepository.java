package com.algomeet.signalservice.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;

import jakarta.transaction.Transactional;

public interface MessageBackupRepository extends MongoRepository<MessageBackupDocument, String> {	
	Page<MessageBackupDocument> findByConversationId(
			String conversationId, Pageable pageable);
	
	Page<MessageBackupDocument> findByConversationIdAndStanzaIdGreaterThan(
	        String conversationId, String stanzaId, Pageable pageable);

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

    @Aggregation(pipeline = {
    		"{ $match: { $or: [ " +
    				"{ userKey: ?0, senderKey: ?0, receiverKey: ?1 }, " +
    				"{ userKey: ?0, senderKey: ?1, receiverKey: ?0 } " +
    				"] } }",

    				"{ $group: { " +
    						"_id: null, " +
    						"totalSize: { $sum: { $ifNull: ['$size', 0] } }, " +
    						"messageCount: { $sum: 1 } " +
    						"} }"
    })
    List<ConversationStorageStats> getConversationStorageStats(String userA, String userB);
}
