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
import com.algomeet.signalservice.repository.projection.MessageBackupView;

import jakarta.transaction.Transactional;

public interface MessageBackupRepository extends MongoRepository<MessageBackupDocument, UUID> {	
	List<MessageBackupDocument> findByConversationIdAndStanzaIdLessThanAndHiddenAtIsNull(
	        String conversationId, UUID stanzaId, Pageable pageable);

	List<MessageBackupDocument> findByConversationIdAndStanzaIdGreaterThanAndHiddenAtIsNull(
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
     * Finds a list of message backup documents matching a collection of message IDs 
     * and a specific user key.
     * <p>
     * This method utilizes Spring Data's method-name derivation to generate a MongoDB 
     * {@code $in} query under the hood.
     * </p>
     *
     * @param messageIds a {@link List} of {@link UUID}s representing the target messages
     * @param userKey    the {@link UUID} of the user who owns or is authorized to access these backups
     * @return a {@link List} of matching {@link MessageBackupView}s, or an empty list if no matches are found
     */
    List<MessageBackupView> findByMessageIdInAndUserKey(List<UUID> messageIds, UUID userKey);


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
	
	/**
	 * Retrieves a lightweight, read-only projection of a specific message backup by its unique ID.
	 * * @param messageId The globally unique identifier of the target message record (_id).
	 * @return An Optional containing the MessageBackupView if found, otherwise empty.
	 */
	Optional<MessageBackupView> findMessageBackupViewByMessageId(UUID messageId);
	
	/**
	 * Fetches a list of related messages (such as reactions, or message edits) 
	 * that refer to a specific set of message IDs belonging to a particular user.
	 * <p>
	 * <strong>Index Optimization Note:</strong> This method executes optimally when paired 
	 * with the compound index <code>{ "userKey": 1, "targetMessageId": 1 }</code>. This satisfies the 
	 * ESR (Equality, Sort, Range) rule by filtering on the exact 'userKey' first, followed 
	 * by the multi-value criteria on 'targetMessageId'.
	 * * @param userKey    The unique identifier of the user who owns the backup records.
	 * @param messageIds A list of parent message IDs being targeted for thread/reaction retrieval.
	 * @return A list of matching MessageBackupView projections.
	 */
	List<MessageBackupView> findByUserKeyAndTargetMessageIdIn(UUID userKey, List<UUID> messageIds);
}
