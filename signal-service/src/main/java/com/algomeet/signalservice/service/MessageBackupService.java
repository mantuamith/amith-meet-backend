package com.algomeet.signalservice.service;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;
import com.algomeet.signalservice.util.ConversationUtil;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Data
public class MessageBackupService {
	private final MessageBackupRepository repository;
	private final MediaService mediaService;
	private final StringRedisTemplate redisTemplate;

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
	    String userKey = SecurityUtil.getUserKey();

	    // Assign owner of this message backup
	    backup.setUserKey(SecurityUtil.getUserKey());

	    // Build deterministic conversation ID so both directions map to the same thread
	    String conversationId = ConversationUtil.getConversationId(
	            userKey,
	            backup.getSenderKey(),
	            backup.getReceiverKey()
	    );
	    backup.setConversationId(conversationId);

	    /**
	     * Redis distributed lock key to prevent concurrent duplicate inserts
	     * for the same messageId (idempotency + race-condition protection).
	     *
	     * Format:
	     * signal:lock:message-backup:insert:{messageId}
	     */
	    String lockKey = "signal:lock:message-backup:insert:" + backup.getMessageId();

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
	        throw new IllegalStateException("Message insert in progress. Please retry.");
	    }

	    try {

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

	        mediaService.adjustStorageUsage(userKey, req);

	        // Persist message backup into MongoDB
	        return repository.save(backup);

	    } finally {
	        /**
	         * Safely release Redis lock using Lua script to ensure:
	         * - only the lock owner can delete it
	         * - avoids accidental deletion of another process's lock
	         */
	        releaseLock(lockKey, lockValue);
	    }
	}

	/**
	 * Safely releases a Redis distributed lock using a Lua script.
	 *
	 * The script ensures atomic check-and-delete:
	 * - Only deletes the lock if the stored value matches the expected value.
	 * - Prevents race conditions where another process acquires the lock before deletion.
	 *
	 * @param key Redis lock key
	 * @param value expected lock value for ownership validation
	 */
	private void releaseLock(String key, String value) {

	    String script =
	            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
	            "return redis.call('del', KEYS[1]) " +
	            "else return 0 end";

	    redisTemplate.execute(
	            new DefaultRedisScript<>(script, Long.class),
	            Collections.singletonList(key),
	            value
	    );
	}

	public Page<MessageBackupDocument> getConversation(String userKey, String participantKey, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));

		// Get converation ID
		String converationId = ConversationUtil.getConversationId(userKey, participantKey);		
		return repository.findByConversationId(converationId, pageable);
	}

	public MessageBackupDocument getMessage(String messageId) {
		Optional<MessageBackupDocument> backupOpt = repository.findById(messageId);		
		return backupOpt.orElseThrow(() -> new RecordNotFoundException("Message ID not found"));
	}

	public List<MessageBackupDocument> getMessages(List<String> messageIds) {
		List<MessageBackupDocument> messageList = repository.findAllById(messageIds);		
		return messageList;
	}	

	public MessageBackupDocument update(String messageId, MessageBackupDocument backup) {
		backup.setMessageId(messageId);
		Optional<MessageBackupDocument> updateOpt = repository.findById(messageId);

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
		mediaService.adjustStorageUsage(backup.getUserKey(), req);

		return repository.save(updateOpt.map(b -> {
			b.setUserKey(backup.getUserKey());
			b.setEncryptedMessage(backup.getEncryptedMessage());
			b.setSenderKey(backup.getSenderKey());
			b.setReceiverKey(backup.getReceiverKey());
			b.setAlgorithm(backup.getAlgorithm());
			b.setVersion(backup.getVersion());
			b.setSalt(backup.getSalt());
			return b;
		}).get());
	}

	public void delete(String messageId) {
		Optional<MessageBackupDocument> updateOpt = repository.findById(messageId);		
		if (updateOpt.isEmpty()) {
			throw new RecordNotFoundException("Message ID not found");
		}

		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(-1L);
		req.setChatStorageBytesDelta(-updateOpt.get().getSize());
		mediaService.adjustStorageUsage(updateOpt.get().getUserKey(), req);

		repository.deleteById(messageId);
	}	

	@Transactional
	public void deleteConversation(String userKey, String peerKey) {

		ConversationStorageStats stats =
				repository.getConversationStorageStats(userKey, peerKey)
				.stream()
				.findFirst()
				.orElse(new ConversationStorageStats(0L, 0L)); // Or handle empty


				long totalSize = stats != null ? stats.getTotalSize() : 0L;
				long messageCount = stats != null ? stats.getMessageCount() : 0L;

				// Update user storage usage 
				StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
				req.setChatMessageCountDelta(-messageCount);
				req.setChatStorageBytesDelta(-totalSize);
				mediaService.adjustStorageUsage(userKey, req);


				repository.deleteConversation(userKey, peerKey);				
	}	

	public void deleteByUserKey(String userKey ){
		repository.deleteByUserKey(userKey);

		// Delete user storage usage
		mediaService.deleteStorage(userKey);
	}		
}
