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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.common.redis.lock.ChatMessageRetentionLockManager;
import com.algomeet.signalservice.constant.Constants;
import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.document.MessageBackupKey;
import com.algomeet.signalservice.dto.MessageBackupRequest;
import com.algomeet.signalservice.dto.MessageBackupUpdateRequest;
import com.algomeet.signalservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.signalservice.exceptions.MessageInsertInProgressException;
import com.algomeet.signalservice.exceptions.MessageUpdateStatusInProgressException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.MessageBackupMapper;
import com.algomeet.signalservice.publisher.ApplyMessageBackupRetentionStreamPublisher;
import com.algomeet.signalservice.publisher.PurgeMessageBackupStreamPublisher;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;
import com.algomeet.signalservice.repository.projection.MessageBackupView;
import com.algomeet.signalservice.util.ConversationUtil;
import com.algomeet.signalservice.util.DeleteMediaUtil;
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
	private final DeleteMediaUtil deleteMediaUtil;
	private final PurgeMessageBackupStreamPublisher purgeMessageBackupStreamPublisher;
	private final ApplyMessageBackupRetentionStreamPublisher applyMessageBackupRetentionStreamPublisher;
	private final ChatMessageRetentionLockManager chatMessageRetentionLockManager;
	private final ConversationSettingsCacheService conversationSettingsCacheService;

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
	public MessageBackupDocument insert(MessageBackupRequest backupReq) {
		// Resolve the authenticated user's identity from security context
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
		
		// FIX 1: Assign the mapped entity result directly to your local variable
		MessageBackupDocument backup = MessageBackupMapper.toEntity(userKey, backupReq);
		
		// Build deterministic conversation ID so both directions map to the same thread
		String conversationId = ConversationUtil.getConversationId(
				userKey.toString(),
				backupReq.getSenderKey().toString(),
				backupReq.getReceiverKey().toString()
				);
		backup.setConversationId(conversationId);

		/**
		 * Redis distributed lock key to prevent concurrent duplicate inserts
		 * for the same messageId (idempotency + race-condition protection).
		 *
		 * Format:
		 * signal:lock:message-backup:insert:{messageId}
		 */
		// FIX 2: Now backup.getMessageId() will safely return the UUID instead of null
		String lockKey = "signal:lock:mb:insert:" + userKey + ":" + backup.getMessageId();

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

		// Retrieve and set message retention
		ConversationSettings conversationSettings = conversationSettingsCacheService.getCachedSettings(backupReq.getSenderKey(), backupReq.getReceiverKey());
		Instant purgeAt = (conversationSettings.getMessageRetentionDays() != null && conversationSettings.getMessageRetentionDays() != -1) 
                ? Instant.now().plus(conversationSettings.getMessageRetentionDays(), ChronoUnit.DAYS)
                : null;
		backup.setPurgeAt(purgeAt);
		
		// Persist message backup into MongoDB
		return repository.insert(backup);
	}

	public List<MessageBackupDocument> getConversationMessagesBefore(UUID userKey, UUID peerKey, UUID stanzaId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, FIELD_STANZA_ID));

		// Get converation ID
		String converationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());		
		return repository.findByConversationIdAndStanzaIdLessThanAndDeletedAtIsNullAndHiddenAtIsNullOrderByStanzaIdDesc(converationId, stanzaId, pageable);
	}

	public List<MessageBackupDocument> getConversationMessagesAfter(UUID userKey, UUID peerKey, UUID stanzaId, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, FIELD_STANZA_ID));

		// Get converation ID
		String converationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());		
		return repository.findByConversationIdAndStanzaIdGreaterThanAndDeletedAtIsNullAndHiddenAtIsNullOrderByStanzaIdAsc(converationId, stanzaId, pageable);
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
		.include(FIELD_SENT_AT)
		.include(FIELD_DELIVERED_AT)
		.include(FIELD_READ_AT)
		.include(FIELD_DELETED_AT)
		.include(FIELD_HIDDEN_AT)
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
				conversationStartMessage.setId(new MessageBackupKey(userKey, Constants.NIL_UUID));
				
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
		if (CollectionUtils.isEmpty(messageIds)) {
			return List.of();
		}
		
		UUID currentUserKey = UUID.fromString(SecurityUtil.getUserKey());
		
		List<MessageBackupDocument> messageList = repository.findAllById(messageIds.stream()
				.map(mid -> new MessageBackupKey(currentUserKey, mid))
				.toList());		
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
				Aggregation.match(Criteria.where(FIELD_USER_KEY).is(userKey).
						and(FIELD_DELETED_AT).is(null).and(FIELD_HIDDEN_AT).is(null)),

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

	public MessageBackupDocument update(UUID userKey,  UUID messageId, MessageBackupUpdateRequest backupReq) {	
		Optional<MessageBackupDocument> updateOpt = repository.findByMessageIdAndUserKey(messageId, userKey);

		if (updateOpt.isEmpty()) {
			throw new RecordNotFoundException("Message ID not found");
		}

		// Set the message size
		if (backupReq.getSize() == null || backupReq.getSize() == 0) {
			backupReq.setSize(backupReq.getEncryptedMessage() != null 
					? backupReq.getEncryptedMessage().getBytes(Charset.forName("utf-8")).length : 0L);
		}

		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatStorageBytesDelta(backupReq.getSize() - updateOpt.get().getSize());
		mediaService.adjustStorageUsage(userKey.toString(), req);			

		return repository.save(updateOpt.map(existing -> {						
			// Encryption Metadata
			if (backupReq.getEncryptedMessage() != null) existing.setEncryptedMessage(backupReq.getEncryptedMessage());
			if (backupReq.getAlgorithm() != null) existing.setAlgorithm(backupReq.getAlgorithm());
			if (backupReq.getVersion() != null) existing.setVersion(backupReq.getVersion());
			if (backupReq.getSalt() != null) existing.setSalt(backupReq.getSalt());
		
			// Relationships & Sync
			if (backupReq.getTargetMessageId() != null) existing.setTargetMessageId(backupReq.getTargetMessageId());
			if (backupReq.getReplyToMessageId() != null) existing.setReplyToMessageId(backupReq.getReplyToMessageId());
				
			if (backupReq.getSize() != null) existing.setSize(backupReq.getSize());
			
			existing.setUpdateCursorId(UuidCreator.getTimeOrderedEpoch());

			// Note: timestamp is usually 'Instant.now()' on creation, 
			// but you can update it here if you want to track 'last modified'			
			existing.setModifiedAt(Instant.now());
			
			return existing;
		}).get());
	}
	
	public void delete(UUID userKey, List<UUID> messageIds) {
		List<MessageBackupView> forDeleteList = repository.findViewByMessageIdInAndUserKey(messageIds, userKey);	
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

		repository.deleteAllById(forDeleteList.stream()
				.map(msg -> new MessageBackupKey(userKey, msg.getMessageId()))
				.toList());
	}	

	@Transactional
	public void deleteConversation(UUID userKey, UUID peerKey, UUID lastStanzaId) {
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
		
		/** Revoke access to media files before deleting the conversation */
		deleteMediaUtil.deleteMediaFilesForDeleteConversation(converationId, lastStanzaId, userKey);

		repository.deleteByUserKeyAndConversationId(userKey, converationId);				
	}	

	public void deleteByUserKey(UUID userKey ){
		// Send user key to redis stream
		purgeMessageBackupStreamPublisher.publish(userKey);
	}	

	public void updateStatus(List<UUID> messageIds, String timestampField, Long timestamp) {
		if (CollectionUtils.isEmpty(messageIds)) {
			return;
		}
		
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());	
		
		Iterator<UUID> it = messageIds.iterator();
		while (it.hasNext()) {
			UUID messageId = it.next();
			/**
			 * Redis distributed lock key to prevent concurrent duplicate inserts
			 * for the same messageId (idempotency + race-condition protection).
			 *
			 * Format:
			 * signal:lock:message-backup:insert:{messageId}
			 */
			String lockKey = "signal:lock:mb:update-status:" + userKey + ":" + messageId;

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
			
			if(Boolean.FALSE.equals(acquired)) {
				it.remove();
			}
		}

		// No message locks were acquired, indicating that another process is currently updating the status of these messages.
		if (CollectionUtils.isEmpty(messageIds)) {
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
				
				// Revoke user access to retracted messages
				deleteMediaUtil.deleteMediaFilesForRetractedMessages(messageIds, userKey);
				
			} else if (FIELD_HIDDEN_AT.equals(timestampField)) {
				// Hide related messages
				hideUtil.hideRelatedMessages(userKey, messageIds);
				
				// Revoke user access to hidden messages
				deleteMediaUtil.deleteMediaFilesForHiddenMessages(messageIds, userKey);
			}
		} else {
			
			log.warn("Message backup message IDs not found: {} " + messageIds);
		}
	}
		
	public Optional<MessageBackupView> getConversationLastSent(UUID userKey, UUID peerKey, UUID senderKey) {
		// Get conversation ID
		String conversationId = ConversationUtil.getConversationId(userKey.toString(), peerKey.toString());	
		
		return repository.findFirstByConversationIdAndSenderKeyAndDeletedAtIsNullAndHiddenAtIsNullOrderByStanzaIdDesc(conversationId, senderKey);
	}
	
	public void purgeMessageBackup(UUID userKey){
		// Update purgeAt value
		repository.updatePurgeAtByUserKey(userKey, Instant.now());
		
		// Delete user storage usage
		mediaService.deleteStorage(userKey.toString());
	}
	
	public void applyMessageRetentionPolicy(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
		if (chatMessageRetentionLockManager.isLocked(userKey, peerKey)) {
			throw new IllegalStateException("Could not acquire retention update lock.");
		}
		
		applyMessageBackupRetentionStreamPublisher.publish(userKey, peerKey, messageRetentionDays);
	}
		
	public void applyMessageBackupRetention(UUID userKey, UUID peerKey, Integer messageRetentionDays) {
		ChatMessageRetentionLockManager.LockToken lockToken = chatMessageRetentionLockManager.acquireLock(userKey, peerKey);
	    
	    // If lock is not acquired, another process is already inserting this message
	    if (lockToken == null) {
	        throw new IllegalStateException("Could not acquire retention update lock.");
	    }
	    
	    try {     
	        updatePurgeAtByToAndFrom(peerKey, userKey, messageRetentionDays);    

	    } finally {
	        // Always release the lock in the finally block so it clears even if repository throws an error
	        try {
	        	chatMessageRetentionLockManager.releaseLock(lockToken);
	        } catch (Exception ex) {
	            log.error("Failed to release distributed lock footprint for key: {}", lockToken, ex);
	        }
	    }
	}
		
	public List<MessageBackupDocument> fetchMessagesByIds(List<UUID> messageIds, UUID currentUserKey) {
		return repository.findByMessageIdInAndUserKey(messageIds, currentUserKey);
	}	
	
	public long updatePurgeAtByToAndFrom(UUID to, UUID from, Integer messageRetentionDays) {		
		// Update backup retention records for the requestor
        String requestorConversationId = ConversationUtil.getConversationId(to.toString(), from.toString());   
        
        // Update backup retention records for the peer
        String peerConversationId = ConversationUtil.getConversationId(from.toString(), to.toString()); 
		
	    // Wrap each distinct logical branch inside a separate Criteria instance
	    Query query = new Query(
	        new Criteria().orOperator(
	                Criteria.where(MessageBackupDocument.FIELD_CONVERSATION_ID).is(requestorConversationId),
	                Criteria.where(MessageBackupDocument.FIELD_CONVERSATION_ID).is(peerConversationId)
	            )
	    );

	    // Fallback if retention days is null or explicit flag
	    if (messageRetentionDays == null || messageRetentionDays == -1) {
	        AggregationUpdate clearUpdate = AggregationUpdate.update().set(MessageBackupDocument.FIELD_PURGE_AT).toValue(null);
	        UpdateResult result = mongoTemplate.updateMulti(query, clearUpdate, MessageBackupDocument.class);
	        return result.getModifiedCount();
	    }

	    // Convert days to milliseconds for the calculation
	    long retentionMs = (long) messageRetentionDays * 86400000L;

	    // Direct BSON Aggregation Expression evaluating field rules database-side
	    AggregationUpdate pipelineUpdate = AggregationUpdate.update()
	        .set(MessageBackupDocument.FIELD_PURGE_AT)
	        .toValue(
	            new Document("$add", List.of(
	                new Document("$ifNull", List.of("$" + MessageBackupDocument.FIELD_TIMESTAMP, "$$NOW")),
	                retentionMs
	            ))
	        );

	    UpdateResult result = mongoTemplate.updateMulti(query, pipelineUpdate, MessageBackupDocument.class);
	    return result.getModifiedCount();
	}
}
