package com.algomeet.xmpp.chatservice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.publisher.MessageMediaDeleteEventPublisher;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.projection.MucMessagePurgeView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredMucMessageScheduler {

	private final MucMessageRepository messageRepository;
	private final MessageMediaDeleteEventPublisher messageMediaDeleteEventStreamPublisher;
	private final ReactiveStringRedisTemplate redisTemplate; 

	private static final String LOCK_KEY = "lock:scheduler:expired-muc-messages-purge";
	
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
		long ttlMinutes = 30; // 30-minute lease window for safety

		log.info("Attempting to acquire distributed lock for MUC message purge...");

		redisTemplate.opsForValue()
			.setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes))
			.flatMap(acquired -> {
				if (!Boolean.TRUE.equals(acquired)) {
					log.debug("Purge execution skipped: Another cluster node holds the MUC scheduler lock key.");
					return Mono.empty();
				}

				log.info("Distributed lock acquired successfully [Token: {}]. Starting MUC message purge job...", lockValue);
				return executePurgePipeline()
					// Guarantee lock release happens after processing completes
					.flatMap(totalDeleted -> releaseLock(lockValue).thenReturn(totalDeleted))
					// Ensure lock is safely released if pipeline fails mid-stream
					.onErrorResume(ex -> releaseLock(lockValue).then(Mono.error(ex)));
			})
			.subscribe(
				totalDeleted -> log.info("Successfully completed MUC purge cycle. Total documents purged: {}", totalDeleted),
				error -> log.error("Critical failure encountered during MUC message purge orchestration pipeline", error)
			);
	}

	/**
	 * Handles core reactive loop retrieval, processing stream publisher events, and data cleanup.
	 */
	private Mono<Integer> executePurgePipeline() {
		Instant now = Instant.now();

		return messageRepository.findByPurgeAtLessThanEqual(now)
			.buffer(500) 
			.flatMap(batch -> {
				List<UUID> idsToDelete = batch.stream()
						.map(MucMessagePurgeView::getId)
						.toList();

				log.debug("Purging a batch of {} expired MUC messages", idsToDelete.size());

				// Handle attachments cleanup			
				batch.stream()
					.filter(view -> view.getMediaIds() != null && !view.getMediaIds().isEmpty())
					.forEach(view -> {
						messageMediaDeleteEventStreamPublisher.publish(
							null, 
							view.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet()), 
							null,
							view.getRoomId() != null ? view.getRoomId().toString() : null, 
							view.getMessageId().toString()
						).subscribe();
					});
				
				return messageRepository.deleteAllById(idsToDelete)
						.thenReturn(idsToDelete.size());
			})
			.reduce(0, Integer::sum);
	}

	/**
	 * Atomically releases the lock via Lua execution.
	 */
	private Mono<Void> releaseLock(String lockValue) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class);
		
		return redisTemplate.execute(script, Collections.singletonList(LOCK_KEY), Collections.singletonList(lockValue))
			.next()
			.doOnNext(released -> {
				if (Long.valueOf(1L).equals(released)) {
					log.info("Distributed lock safely released [Token: {}].", lockValue);
				} else {
					log.warn("Lock release bypassed: Lock lease expired or was overridden by another process context.");
				}
			})
			.doOnError(ex -> log.error("Failed to clean up MUC lock key reference footprint from cache engine", ex))
			.then();
	}
}