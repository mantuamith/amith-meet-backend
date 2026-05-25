package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;
import com.algomeet.signalservice.repository.projection.MessageMetadataProjection;

import jakarta.transaction.Transactional;

public interface MessageBackupRepository extends MongoRepository<MessageBackupDocument, UUID> {	
	List<MessageBackupDocument> findByConversationIdAndStanzaIdLessThan(
			String conversationId, UUID stanzaId, Pageable pageable);

	List<MessageBackupDocument> findByConversationIdAndStanzaIdGreaterThan(
			String conversationId, UUID stanzaId, Pageable pageable);

	// Custom delete query for both sides of conversation
	@Modifying
	@Query(value = "{ 'userKey': ?0, 'conversationId': ?1 }", delete = true)
	@Transactional
	void deleteByUserKeyAndConversationId(UUID userKey, String conversationId);

	@Modifying
	@Transactional
	void deleteByUserKey(UUID userKey);

	@Aggregation(pipeline = {
			// 1. Filter by the record owner and the specific conversation
			"{ $match: { 'userKey': ?0, 'conversationId': ?1 } }",

			// 2. Aggregate the metrics
			"{ $group: { " +
			"_id: null, " +
			"totalSize: { $sum: { $ifNull: ['$size', 0] } }, " +
			"messageCount: { $sum: 1 } " +
			"} }"
	})
	List<ConversationStorageStats> getConversationStorageStats(UUID userKey, String conversationId);

	/**
	 * Retrieves a message backup only if it belongs to the specified user.
	 * Use this instead of findById for better security and index locality.
	 */
	Optional<MessageBackupDocument> findByMessageIdAndUserKey(UUID messageId, UUID userKey);


	/**
	 * Retrieves the absolute oldest message (smallest stanzaId) within a specific 
	 * conversation for a given user.
	 * 
	 * @param userKey        The record owner.
	 * @param conversationId The specific 1:1 or group chat ID.
	 * @return The first message ever sent/received in this chat, or empty.
	 */
	Optional<MessageBackupDocument> findFirstByUserKeyAndConversationIdOrderByStanzaIdAsc(
	    UUID userKey, 
	    String conversationId
	);
	
	Optional<MessageMetadataProjection> findProjectedByMessageId(UUID messageId);
}
