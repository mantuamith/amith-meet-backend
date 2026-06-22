package com.algomeet.signalservice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.algomeet.signalservice.publisher.MessageMediaDeleteEventPublisher;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.repository.projection.MessageBackupPurgeView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredBackupMessageScheduler {

	private final MessageBackupRepository messageBackupRepository;
	private final MessageMediaDeleteEventPublisher messageMediaDeleteEventPublisher;
	private final StringRedisTemplate redisTemplate; 

	private static final String LOCK_KEY = "lock:scheduler:expired-backup-messages-purge";
	
	// Lua script ensuring atomic "check-then-delete" lock releases to avoid cross-node lease hijacking
	private static final String RELEASE_LUA_SCRIPT = 
			"if redis.call('get', KEYS[1]) == ARGV[1] then " +
			"    return redis.call('del', KEYS[1]) " +
			"else " +
			"    return 0 " +
			"end";

	/**
	 * Executes every hour on the hour.
	 */
	@Scheduled(cron = "0 0 * * * *")
	public void purgeExpiredMessages() {
		String lockValue = UUID.randomUUID().toString();
		long ttlMinutes = 30; // 30-minute safety window for a heavy DB purge

		log.info("Attempting to acquire distributed lock for message purge...");

		boolean acquired = false;
		try {
			// Try to acquire distributed lock synchronously
			Boolean result = redisTemplate.opsForValue()
					.setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes));
			
			acquired = Boolean.TRUE.equals(result);

			if (!acquired) {
				log.debug("Purge execution skipped: Another cluster node holds the scheduler lock key.");
				return;
			}

			log.info("Distributed lock acquired successfully [Token: {}]. Starting message purge job...", lockValue);
			
			// Execute core database processing imperatively
			int totalDeleted = executePurgePipeline();
			
			log.info("Successfully completed purge cycle. Total documents purged: {}", totalDeleted);

		} catch (Exception e) {
			log.error("Critical failure encountered during message purge orchestration pipeline", e);
		} finally {
			if (acquired) {
				try {
					// Execute atomic release validation via Redis engine
					Long released = redisTemplate.execute(
							new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class),
							Collections.singletonList(LOCK_KEY),
							lockValue
					);

					if (Long.valueOf(1L).equals(released)) {
						log.info("Distributed lock safely released [Token: {}].", lockValue);
					} else {
						log.warn("Lock release bypassed: Lock lease expired or was overridden by another process context.");
					}
				} catch (Exception ex) {
					log.error("Failed to clean up lock key reference footprint from cache engine", ex);
				}
			}
		}
	}

	/**
	 * Handles core blocking processing logic for retrieving and purging the data stream.
	 */
	private int executePurgePipeline() {
		int totalRowDeletedCount = 0;
		Instant now = Instant.now();
		final int maxLimit = 200;
		int page = 0;

		// 1. Enforce pagination with a strict limit of 200 items max
	    Pageable pageable = PageRequest.of(page, maxLimit);
	    
	    while (true) {
			// Fetch the items synchronously from your standard MongoRepository
			List<MessageBackupPurgeView> expiredMessages = messageBackupRepository.findByPurgeAtLessThanEqual(now, pageable);
	
			if (expiredMessages.isEmpty()) {
				return totalRowDeletedCount;
			}
	
			log.info("Found {} expired backup messages eligible for cleanup.", expiredMessages.size());
	
			// Process attachments cleanup notifications
			expiredMessages.stream()
				.filter(view -> view.getMediaIds() != null && !view.getMediaIds().isEmpty() && view.getDeletedAt() == null)
				.forEach(view -> {
					try {
						messageMediaDeleteEventPublisher.publish(
							null, 
							view.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet()), 
							Stream.of(view.getSenderKey(), view.getReceiverKey())
								.filter(Objects::nonNull)
								.map(UUID::toString)
								.collect(Collectors.toSet()),
							null, 
							view.getMessageId().toString()
						);
					} catch (Exception e) {
						log.error("Failed to publish media delete event for message ID: {}", view.getMessageId(), e);
					}
				});
	
			// Extract IDs to purge from database
			List<UUID> idsToDelete = expiredMessages.stream()
					.map(MessageBackupPurgeView::getStanzaId)
					.toList();
	
			log.debug("Purging {} expired backup message records from the database", idsToDelete.size());
			
			// Use standard MongoRepository built-in batch deletion tool
			messageBackupRepository.deleteAllById(idsToDelete);
			totalRowDeletedCount += idsToDelete.size();
			page += page + 1;
			
			if (idsToDelete.size() < maxLimit) {
				break;
			}
	    }

		return totalRowDeletedCount;
	}
}