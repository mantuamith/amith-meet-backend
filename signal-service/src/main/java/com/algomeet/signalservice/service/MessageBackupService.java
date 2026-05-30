package com.algomeet.signalservice.service;

import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_CONVERSATION_ID;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_DELETED_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_DELIVERED_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_ENCRYPTED_MSG;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_HIDDEN_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_MESSAGE_ID;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_READ_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_RECEIVER_KEY;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_SENDER_KEY;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_SENT_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_SIZE;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_STANZA_ID;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_UPDATE_CURSOR_ID;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_USER_KEY;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.signalservice.constant.Constants;
import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.signalservice.exceptions.MessageInsertInProgressException;
import com.algomeet.signalservice.exceptions.MessageUpdateStatusInProgressException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;
import com.algomeet.signalservice.repository.projection.MessageBackupView;
import com.algomeet.signalservice.util.ConversationUtil;
import com.algomeet.signalservice.util.HideUtil;
import com.algomeet.signalservice.util.RetractUtil;
import com.algomeet.signalservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;
import com.mongodb.client.result.UpdateResult;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
@Data
public class MessageBackupService {
	private final MessageBackupRepository repository;
	private final MediaService mediaService;
	private final StringRedisTemplate redisTemplate;
	private final MongoTemplate mongoTemplate;
	private final RetractUtil retractUtil;
	private final HideUtil hideUtil;

	/**
	 * Inserts a message backup document into MongoDB with concurrency protection,
	 * deterministic conversation mapping, and storage usage tracking.
	 *
	 * This method performs the following steps:
	 * 1. Resolves the authenticated user's key.
	 * 2. Builds a deterministic conversationId for 1:1 or entity-based messaging.
	 * 3. Applies a Redis distributed lock to prevent duplicate inserts under concurrent requests.
	 * 4. Calculates encrypted message size if not already provided.
	 * 5. Updates user storage usage statistics (message count + bytes consumed).
	 * 6. Persists the message backup into MongoDB.
	 *
	 * @param backup message backup document to be persisted
	 * @return persisted MessageBackupDocument
	 */
	public MessageBackupDocument insert(MessageBackupDocument backup) {
		// Resolve the authenticated user's identity from security context
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());

		// Assign owner of this message backup
		backup.setUserKey(userKey);

		// Build deterministic conversation ID so both directions map to the same thread
		String conversationId = ConversationUtil.getConversationId(
				userKey.toString(),
				backup.getSenderKey().toString(),
				backup.getReceiverKey().toString()
				);
		backup.setConversationId(conversationId);

		// Set stanza ID if empty
		if (backup.getStanzaId() == null) {
			backup.setStanzaId(UuidCreator.getTimeOrderedEpoch());
		}

		/**
		 * Redis distributed lock key to prevent concurrent duplicate inserts
		 * for the same messageId (idempotency + race-condition protection).
		 *
		 * Format:
		 * signal:lock:message-backup:insert:{messageId}
		 */
		String lockKey = "signal:lock:mb:insert:" + backup.getMessageId();

		/**
		 * Lock value used for safe release verification.
		 * NOTE: In production systems, this should ideally be a UUID to ensure ownership safety.
		 */
		String lockValue = UUID.randomUUID().toString();

		// Lock TTL ensures deadlock prevention in case of unexpected failures
		long ttlSeconds = 5;

		// Attempt to acquire distributed lock
		boolean acquired = redisTemplate.opsForValue()
				.setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds));

		// If lock is not acquired, another process is already inserting this message
		if (!Boolean.TRUE.equals(acquired)) {
			throw new MessageInsertInProgressException();
		}

		/**
		 * Compute encrypted message size if not explicitly provided.
		 * This is used for:
		 * - user storage quota tracking
		 * - billing / analytics
		 * - storage optimization metrics
		 */
		if (backup.getSize() == null || backup.getSize() == 0) {
			backup.setSize(
					backup.getEncryptedMessage() != null
					? backup.getEncryptedMessage()
							.getBytes(Charset.forName("utf-8")).length
							: 0L
					);
		}

		/**
		 * Adjust user storage usage atomically:
		 * - increments message count by 1
		 * - increments stored bytes by message size
		 *
		 * This ensures accurate quota tracking per user.
		 */
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(1L);
		req.setChatStorageBytesDelta(backup.getSize());

		mediaService.adjustStorageUsage(userKey.toString(), req);

		// Persist message backup into MongoDB
		return repository.save(backup);
	}

	public List<MessageBackupDocument> getConversationMessagesBefore(UUID userKey, UUID peerKey, UUID stanzaId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, FIELD_STANZA_ID));

		// Get converation ID
		String converationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());		
		return repository.findByConversationIdAndStanzaIdLessThanAndHiddenAtIsNull(converationId, stanzaId, pageable);
	}

	public List<MessageBackupDocument> getConversationMessagesAfter(UUID userKey, UUID peerKey, UUID stanzaId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, FIELD_STANZA_ID));

		// Get converation ID
		String converationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());		
		return repository.findByConversationIdAndStanzaIdGreaterThanAndHiddenAtIsNull(converationId, stanzaId, pageable);
	}

	public List<MessageBackupDocument> getMessageUpdates(
			UUID userKey,
			UUID peerKey,
			UUID cursorStanzaId,
			UUID limitStanzaId,
			int page, 
			int size
			) {
		// 1. Initialize Spring Data Pagination
		if (page == 0) {
			size = Math.max(1, size - 1); // Guard against size dropping below 1
		}

		Pageable pageable = PageRequest.of(page, size);
		Query query = new Query();

		String conversationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());

		// Filter: conversationId AND updateCursorId > cursorStanzaId AND stanzaId <= limitStanzaId
		query.addCriteria(
				Criteria.where(FIELD_CONVERSATION_ID).is(conversationId)
				.and(FIELD_UPDATE_CURSOR_ID).gt(cursorStanzaId)
				.and(FIELD_STANZA_ID).lte(limitStanzaId)
				);

		// Projection
		query.fields()
		.include(FIELD_MESSAGE_ID)
		.include(FIELD_STANZA_ID)
		.include(FIELD_SENDER_KEY)
		.include(FIELD_RECEIVER_KEY)
//		.include(FIELD_ENCRYPTED_MSG)
//		.include(FIELD_ALGORITHM)
//		.include(FIELD_VERSION)
//		.include(FIELD_SALT)
		.include(FIELD_SENT_AT)
		.include(FIELD_DELIVERED_AT)
		.include(FIELD_READ_AT)
		.include(FIELD_DELETED_AT)
		.include(FIELD_HIDDEN_AT)
//		.include(FIELD_EDIT_COUNT)
		;

		// 2. Attach Sort and Pagination Bounds to Query
		query.with(Sort.by(Sort.Direction.DESC, FIELD_STANZA_ID));
		query.with(pageable);

		// 3. Collect to standard ArrayList for significantly faster processing and serialization
		List<MessageBackupDocument> modifiedRecords = new ArrayList<>(mongoTemplate.find(query, MessageBackupDocument.class));

		// 4. Inject Conversation Start Indicator ONLY on Page 0
		if (page == 0) {
			Optional<MessageBackupDocument> optCurrentFirstMessage = repository.findFirstByUserKeyAndConversationIdOrderByStanzaIdAsc(userKey, conversationId);

			if (optCurrentFirstMessage.isPresent()) {
				MessageBackupDocument conversationStartMessage = optCurrentFirstMessage.get();
				// Otherwise, mark it and inject it cleanly as the first item
				conversationStartMessage.setStartOfConversation(true);
				// Lighten the message
				conversationStartMessage.setEncryptedMessage(null);
				modifiedRecords.add(0, conversationStartMessage);
				
			} else {
				MessageBackupDocument conversationStartMessage = new MessageBackupDocument();
				conversationStartMessage.setStartOfConversation(true);
				conversationStartMessage.setMessageId(Constants.NIL_UUID);
				conversationStartMessage.setStanzaId(Constants.NIL_UUID);
				
				return List.of(conversationStartMessage);
			}
		}

		return modifiedRecords;
	}

	public MessageBackupDocument getMessage(UUID userKey, UUID messageId) {
		Optional<MessageBackupDocument> backupOpt = repository.findByMessageIdAndUserKey(messageId, userKey);	
		return backupOpt.orElseThrow(() -> new RecordNotFoundException("Message ID not found"));
	}

	public List<MessageBackupDocument> getMessages(List<UUID> messageIds) {
		List<MessageBackupDocument> messageList = repository.findAllById(messageIds);		
		return messageList;
	}

	public List<String> getConversationContacts(UUID userKey){
		List<String> conversationIds = findUniqueConversationIds(userKey);

		if (!CollectionUtils.isEmpty(conversationIds)) {
			return conversationIds.stream().map(ConversationUtil::getPeerKey).toList();
		}

		return List.of();
	}

	private List<String> findUniqueConversationIds(UUID userKey) {
	    // 1. Memory guardrail is critical for high-volume pipeline processing
	    AggregationOptions options = AggregationOptions.builder().allowDiskUse(true).build();

	    Aggregation aggregation = Aggregation.newAggregation(
	            // 2. Instantly target the user space via index prefix
	            Aggregation.match(Criteria.where(FIELD_USER_KEY).is(userKey)),

	            // 3. Walk backwards down your compound index: {'userKey': 1, 'stanzaId': -1, 'conversationId': 1}
	            // Because it's a UUIDv7, sorting DESC here guarantees the newest message is read FIRST.
	            Aggregation.sort(Sort.Direction.DESC, FIELD_STANZA_ID),

	            // 4. Group by conversationId. MongoDB optimizes this streaming input: 
	            // The first record it sees per conversation is inherently the maximum/newest!
	            Aggregation.group(FIELD_CONVERSATION_ID),

	            // 5. Project out the resulting conversation identifier
	            Aggregation.project().and("_id").as(FIELD_CONVERSATION_ID)
	    )
	    .withOptions(options);

	    AggregationResults<ConversationIdResult> results = mongoTemplate.aggregate(
	            aggregation, "message_backups", ConversationIdResult.class
	    );

	    return results.getMappedResults().stream()
	            .map(ConversationIdResult::getConversationId)
	            .collect(Collectors.toList());
	}
	
	// Simple DTO for mapping
	@Data
	private static class ConversationIdResult {
		private String conversationId;
	}

	public List<MessageBackupDocument> findUniqueConversationsWithFullDetails(UUID userKey) {
		AggregationOptions options = AggregationOptions.builder().allowDiskUse(true).build();

		Aggregation aggregation = Aggregation.newAggregation(
				// 1. Filter only for messages belonging to this user
				// This perfectly utilizes your compound index: idx_user_inbox_view {'userKey': 1, 'timestamp': -1, 'conversationId': 1}
				Aggregation.match(Criteria.where(FIELD_USER_KEY).is(userKey).and(FIELD_HIDDEN_AT).is(null)),

				// 2. Sort them by timestamp descending BEFORE grouping.
				// This ensures the first document MongoDB encounters per conversation is the newest one.
				Aggregation.sort(Sort.Direction.DESC, FIELD_STANZA_ID),

				// 3. Group by conversationId and capture the entire first document ($first)
				Aggregation.group(FIELD_CONVERSATION_ID)
				.first(Aggregation.ROOT).as("latestMessage"),

				// 4. Flatten the structure so it maps directly back into your document class
				Aggregation.replaceRoot("latestMessage"),

				// 5. Final sort to ensure the entire conversation list is ordered by newest message first
				Aggregation.sort(Sort.Direction.DESC, FIELD_STANZA_ID)
				)
				.withOptions(options); // Attach memory guardrail
		

		AggregationResults<MessageBackupDocument> results = mongoTemplate.aggregate(
				aggregation, "message_backups", MessageBackupDocument.class
				);

		return results.getMappedResults();
	}

	public MessageBackupDocument update(UUID userKey,  UUID messageId, MessageBackupDocument backup) {	
		backup.setMessageId(messageId);
		Optional<MessageBackupDocument> updateOpt = repository.findByMessageIdAndUserKey(messageId, userKey);

		if (updateOpt.isEmpty()) {
			throw new RecordNotFoundException("Message ID not found");
		}

		// Set the message size
		if (backup.getSize() == null || backup.getSize() == 0) {
			backup.setSize(backup.getEncryptedMessage() != null 
					? backup.getEncryptedMessage().getBytes(Charset.forName("utf-8")).length : 0L);
		}

		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatStorageBytesDelta(backup.getSize() - updateOpt.get().getSize());
		mediaService.adjustStorageUsage(backup.getUserKey().toString(), req);

		return repository.save(updateOpt.map(existing -> {
			// Core Identity & Routing
			if (backup.getUserKey() != null) existing.setUserKey(backup.getUserKey());
			if (backup.getConversationId() != null) existing.setConversationId(backup.getConversationId());
			if (backup.getStanzaId() != null) existing.setStanzaId(backup.getStanzaId());

			// Encryption Metadata
			if (backup.getEncryptedMessage() != null) existing.setEncryptedMessage(backup.getEncryptedMessage());
			if (backup.getSenderKey() != null) existing.setSenderKey(backup.getSenderKey());
			if (backup.getReceiverKey() != null) existing.setReceiverKey(backup.getReceiverKey());
			if (backup.getAlgorithm() != null) existing.setAlgorithm(backup.getAlgorithm());
			if (backup.getVersion() != null) existing.setVersion(backup.getVersion());
			if (backup.getSalt() != null) existing.setSalt(backup.getSalt());

			// State & Timestamps
			if (backup.getSentAt() != null) existing.setSentAt(backup.getSentAt());
			if (backup.getDeliveredAt() != null) existing.setDeliveredAt(backup.getDeliveredAt());
			if (backup.getReadAt() != null) existing.setReadAt(backup.getReadAt());
			if (backup.getDeletedAt() != null) existing.setDeletedAt(backup.getDeletedAt());

			// Relationships & Sync
			if (backup.getTargetMessageId() != null) existing.setTargetMessageId(backup.getTargetMessageId());
			if (backup.getReplyToMessageId() != null) existing.setReplyToMessageId(backup.getReplyToMessageId());
			if (backup.getEditCount() != null) existing.setEditCount(backup.getEditCount());
			if (backup.getUpdateCursorId() != null) existing.setUpdateCursorId(backup.getUpdateCursorId());
			if (backup.getSize() != null) existing.setSize(backup.getSize());

			// Note: timestamp is usually 'Instant.now()' on creation, 
			// but you can update it here if you want to track 'last modified'

			return existing;
		}).get());
	}

	public MessageBackupDocument edit(UUID userKey, UUID messageId, MessageBackupDocument backup) {	
		backup.setMessageId(messageId);
		Optional<MessageBackupDocument> updateOpt = repository.findByMessageIdAndUserKey(messageId, userKey);

		if (updateOpt.isEmpty()) {
			throw new RecordNotFoundException("Message ID not found");
		}

		// Set the message size
		if (backup.getSize() == null || backup.getSize() == 0) {
			backup.setSize(backup.getEncryptedMessage() != null 
					? backup.getEncryptedMessage().getBytes(Charset.forName("utf-8")).length : 0L);
		}

		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatStorageBytesDelta(backup.getSize() - updateOpt.get().getSize());
		mediaService.adjustStorageUsage(backup.getUserKey().toString(), req);

		UUID updateStanzaId;		

		if(backup.getUpdateCursorId() != null) {
			updateStanzaId = backup.getUpdateCursorId();
		} else {
			updateStanzaId = UuidCreator.getTimeOrderedEpoch();
		}

		return repository.save(updateOpt.map(b -> {
			b.setEditCount(b.getEditCount() != null ? (b.getEditCount() + 1) : 1);
			b.setUpdateCursorId(updateStanzaId);

			if(StringUtils.hasText(backup.getEncryptedMessage())) {
				b.setEncryptedMessage(backup.getEncryptedMessage());
			}

			if(StringUtils.hasText(backup.getAlgorithm())) {
				b.setAlgorithm(backup.getAlgorithm());
			}

			if(StringUtils.hasText(backup.getVersion())) {
				b.setVersion(backup.getVersion());
			}

			if(StringUtils.hasText(backup.getSalt())) {
				b.setSalt(backup.getSalt());
			}
			return b;
		}).get());
	}

	public void delete(UUID userKey, List<UUID> messageIds) {
		List<MessageBackupView> forDeleteList = repository.findByMessageIdInAndUserKey(messageIds, userKey);	
		if (forDeleteList.isEmpty()) {
			throw new RecordNotFoundException("Message IDs not found");
		}

		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(-1L * forDeleteList.size());
		long totalSize = forDeleteList.stream()
			    .mapToLong(doc -> doc.getSize() != null ? doc.getSize() : 0L)
			    .sum();
		req.setChatStorageBytesDelta(-1 * totalSize);
		mediaService.adjustStorageUsage(forDeleteList.get(0).getUserKey().toString(), req);

		repository.deleteAllById(forDeleteList.stream().map(msg -> msg.getMessageId()).toList());
	}	

	@Transactional
	public void deleteConversation(UUID userKey, UUID peerKey) {
		String converationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());

		ConversationStorageStats stats =
				repository.getConversationStorageStats(userKey, converationId)
				.stream()
				.findFirst()
				.orElse(new ConversationStorageStats(0L, 0L)); // Or handle empty


		long totalSize = stats != null ? stats.getTotalSize() : 0L;
		long messageCount = stats != null ? stats.getMessageCount() : 0L;

		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(-messageCount);
		req.setChatStorageBytesDelta(-totalSize);
		mediaService.adjustStorageUsage(userKey.toString(), req);

		repository.deleteByUserKeyAndConversationId(userKey, converationId);				
	}	

	public void deleteByUserKey(UUID userKey ){
		repository.deleteByUserKey(userKey);

		// Delete user storage usage
		mediaService.deleteStorage(userKey.toString());
	}	

	public void updateStatus(List<UUID> messageIds, String timestampField, Long timestamp) {
		if (CollectionUtils.isEmpty(messageIds)) {
			return;
		}
		
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());	
		/**
		 * Redis distributed lock key to prevent concurrent duplicate inserts
		 * for the same messageId (idempotency + race-condition protection).
		 *
		 * Format:
		 * signal:lock:message-backup:insert:{messageId}
		 */
		String lockKey = "signal:lock:mb:update-status:" + messageIds.get(0);

		/**
		 * Lock value used for safe release verification.
		 * NOTE: In production systems, this should ideally be a UUID to ensure ownership safety.
		 */
		String lockValue = UUID.randomUUID().toString();

		// Lock TTL ensures deadlock prevention in case of unexpected failures
		long ttlSeconds = 5;

		// Attempt to acquire distributed lock
		boolean acquired = redisTemplate.opsForValue()
				.setIfAbsent(lockKey, lockValue, Duration.ofSeconds(ttlSeconds));

		// If lock is not acquired, another process is already inserting this message
		if (!Boolean.TRUE.equals(acquired)) {
			throw new MessageUpdateStatusInProgressException();
		}

		// Atomic update for high-concurrency environments
		Query query = null;		
		if(FIELD_READ_AT.equals(timestampField)) {
			// Uses .lte() to ensure the message ID is less than or equal to the threshold
			// 1. Filter by User Key (Equality match)
			// 2. Filter by Message ID threshold (Range match)
			// 3. Apply the dynamic status timestamp null check last			
			Optional<MessageBackupView> messageOpt = repository.findMessageBackupViewByMessageId(messageIds.get(0));			
			
			if(messageOpt.isPresent()) {
				String conversationId = ConversationUtil.getConversationId(
						userKey.toString(),
						messageOpt.get().getSenderKey().toString(),
						messageOpt.get().getReceiverKey().toString());
				
				query = new Query(
						Criteria.where(FIELD_CONVERSATION_ID).is(conversationId)
						.and(FIELD_SENDER_KEY).is(messageOpt.get().getSenderKey())
						.and(FIELD_STANZA_ID).lte(messageOpt.get().getStanzaId())
						.and(FIELD_READ_AT).isNull()
						);
			}

		} else {			
			query = new Query(Criteria.where(FIELD_MESSAGE_ID).in(messageIds)
					.and(FIELD_USER_KEY).is(userKey));
		}

		Update update = new Update()
				.set(timestampField, timestamp)
				.set(FIELD_UPDATE_CURSOR_ID, UuidCreator.getTimeOrderedEpoch());

		// Clean up message
		if (FIELD_DELETED_AT.equals(timestampField) || FIELD_HIDDEN_AT.equals(timestampField)) {		
			update.set(FIELD_ENCRYPTED_MSG, null);
			// TODO: Calculate the deducted size
			update.set(FIELD_SIZE, 0);
		}

		UpdateResult result = mongoTemplate.updateMulti(query, update, MessageBackupDocument.class);

		if (result.getMatchedCount() >  0) {
			if (FIELD_DELETED_AT.equals(timestampField)) {
				// Retract related messages
				retractUtil.retractRelatedMessages(userKey, messageIds);
				
			} else if (FIELD_HIDDEN_AT.equals(timestampField)) {
				// Hide related messages
				hideUtil.hideRelatedMessages(userKey, messageIds);
			}
		} else {
			
			log.warn("Message backup message IDs not found: {} " + messageIds);
		}
	}
}
