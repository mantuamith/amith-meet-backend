package com.algomeet.signalservice.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.document.MessageBackupKey;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;
import com.algomeet.signalservice.repository.projection.MessageBackupPurgeView;
import com.algomeet.signalservice.repository.projection.MessageBackupView;

public interface MessageBackupRepository extends MongoRepository<MessageBackupDocument, MessageBackupKey> {	
	
	@Query(value = "{ 'conversationId': ?0, '_id.stanzaId': { '$lt': ?1 }, 'deletedAt': null, 'hiddenAt': null }", 
			sort = "{ '_id.stanzaId': -1 }")
	List<MessageBackupDocument> findByConversationIdAndStanzaIdLessThanAndDeletedAtIsNullAndHiddenAtIsNullOrderByStanzaIdDesc(
	        String conversationId, UUID stanzaId, Pageable pageable);

	@Query(value = "{ 'conversationId': ?0, '_id.stanzaId': { '$gt': ?1 }, 'deletedAt': null, 'hiddenAt': null }", 
			sort = "{ '_id.stanzaId': 1 }")
	List<MessageBackupDocument> findByConversationIdAndStanzaIdGreaterThanAndDeletedAtIsNullAndHiddenAtIsNullOrderByStanzaIdAsc(
	        String conversationId, UUID stanzaId, Pageable pageable);

	// Custom delete query for both sides of conversation
	@Query(value = "{ '_id.userKey': ?0, 'conversationId': ?1 }", delete = true)
	void deleteByUserKeyAndConversationId(UUID userKey, String conversationId);

	@Aggregation(pipeline = {
			// 1. Filter by the record owner and the specific conversation
			"{ $match: { '_id.userKey': ?0, 'conversationId': ?1 } }",

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
	@Query("{ 'messageId': ?0, '_id.userKey': ?1 }")
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
	@Query("{ 'messageId': { '$in': ?0 }, '_id.userKey': ?1 }")
    List<MessageBackupView> findByMessageIdInAndUserKey(List<UUID> messageIds, UUID userKey);


	/**
	 * Retrieves the absolute oldest message (smallest stanzaId) within a specific 
	 * conversation for a given user.
	 * * @param userKey        The record owner.
	 * @param conversationId The specific 1:1 or group chat ID.
	 * @return The first message ever sent/received in this chat, or empty.
	 */
	@Query(value = "{ '_id.userKey': ?0, 'conversationId': ?1 }", 
			sort = "{ '_id.stanzaId': 1 }")
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
	@Query("{ '_id.userKey': ?0, 'targetMessageId': { '$in': ?1 } }")
	List<MessageBackupView> findByUserKeyAndTargetMessageIdIn(UUID userKey, List<UUID> messageIds);
	
	@Query(value = "{ 'conversationId': ?0, 'senderKey': ?1, 'deletedAt': null, 'hiddenAt': null }", 
			sort = "{ '_id.stanzaId': -1 }")
	Optional<MessageBackupView> findFirstByConversationIdAndSenderKeyAndDeletedAtIsNullAndHiddenAtIsNullOrderByStanzaIdDesc(		    
		    String conversationId,
		    UUID senderKey
		);
	
	/**
	 * Select messages where purgeAt <= paramter date
	 * */
	@Query(value = "{ 'purgeAt': { '$lte': ?0 } }", sort = "{ '_id.stanzaId': 1 }")
	List<MessageBackupPurgeView> findByPurgeAtLessThanEqual(Instant purgeAt, Pageable pageable);	
	
	/**
	 * Used for for deleting media files.
	 * @param conversationId
	 * @param stanzaId
	 * @param pageable
	 * @return
	 */
	@Query(value = "{ 'conversationId': ?0, '_id.stanzaId': { '$lte': ?1 }, 'deletedAt': null, 'hiddenAt': null, 'mediaIds': { '$ne': null } }", 
			sort = "{ '_id.stanzaId': -1 }")
	List<MessageBackupView> findByConversationIdAndStanzaIdLessThanEqualAndDeletedAtIsNullAndHiddenAtIsNullAndMediaIdsIsNotNullOrderByStanzaIdDesc(
	        String conversationId, UUID stanzaId, Pageable pageable);
	
	/**
	 * Used for for deleting media files.
	 * @param conversationId
	 * @param stanzaId
	 * @param pageable
	 * @return
	 */
	@Query(value = "{ 'conversationId': ?0, '_id.stanzaId': { '$lt': ?1 }, 'deletedAt': null, 'hiddenAt': null, 'mediaIds': { '$ne': null } }", 
			sort = "{ '_id.stanzaId': -1 }")
	List<MessageBackupView> findByConversationIdAndStanzaIdLessThanAndDeletedAtIsNullAndHiddenAtIsNullAndMediaIdsIsNotNullOrderByStanzaIdDesc(
	        String conversationId, UUID stanzaId, Pageable pageable);
		
	@Query("{ '_id.userKey': ?0}") // Target only un-purged records in the message backup
	@Update("{ '$set': { 'purgeAt': ?1 } }")
	Long updatePurgeAtByUserKey(UUID userKey, Instant purgeTime);
	
	
	@Query("{ 'conversationId': ?0}") // target conversations
	@Update("[ { " +
	        "  '$set': { " +
	        "    'purgeAt': { " +
	        "      '$cond': [ " +
	        "        { '$eq': [ ?2, null ] }, " + // Condition: if messageRetentionDays is null
	        "        null, " +                    // Then: set purgeAt to null
	        "        { '$add': [ { '$ifNull': [ '$createdAt', '$$NOW' ] }, { '$multiply': [ ?2, 86400000 ] } ] } " + // Else: run calculation
	        "      ] " +
	        "    } " +
	        "  } " +
	        "} ]")
	Long updatePurgeAtByConversationId(String conversationId, Integer messageRetentionDays);
}