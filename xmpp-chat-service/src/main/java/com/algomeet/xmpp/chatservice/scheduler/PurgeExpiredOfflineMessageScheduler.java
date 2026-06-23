package com.algomeet.xmpp.chatservice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.publisher.DeleteMessageMediaEventPublisher;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.repository.projection.MessagePurgeView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredOfflineMessageScheduler {

	private final OfflineMessageRepository messageRepository;
	private final DeleteMessageMediaEventPublisher messageMediaDeleteEventStreamPublisher;
	private final ReactiveStringRedisTemplate redisTemplate; 

	private static final String LOCK_KEY = "lock:scheduler:expired-offline-messages-purge";
	
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

		redisTemplate.opsForValue()
			.setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes))
			.flatMap(acquired -> {
				if (!Boolean.TRUE.equals(acquired)) {
					log.debug("Purge execution skipped: Another cluster node holds the scheduler lock key.");
					return Mono.empty();
				}

				log.info("Distributed lock acquired successfully [Token: {}]. Starting message purge job...", lockValue);
				return executePurgePipeline()
					// Guarantee that lock release executes after processing completes
					.flatMap(totalDeleted -> releaseLock(lockValue).thenReturn(totalDeleted))
					// Ensure lock is safely released even if the pipeline terminates with an error
					.onErrorResume(ex -> releaseLock(lockValue).then(Mono.error(ex)));
			})
			.subscribe(
				totalDeleted -> log.info("Successfully completed purge cycle. Total documents purged: {}", totalDeleted),
				error -> log.error("Critical failure encountered during message purge orchestration pipeline", error)
			);
	}

	/**
	 * Handles the core reactive processing logic for retrieving and purging the data stream.
	 */
	private Mono<Integer> executePurgePipeline() {
		Instant now = Instant.now();

		return messageRepository.findByPurgeAtLessThanEqual(now)
			.buffer(500) 
			.flatMap(batch -> {
				List<UUID> idsToDelete = batch.stream()
						.map(MessagePurgeView::getStanzaId)
						.toList();

				log.debug("Purging a batch of {} expired messages", idsToDelete.size());

				// Handle attachments cleanup			
				batch.stream()
					.filter(view -> view.getMediaIds() != null && !view.getMediaIds().isEmpty())
					.forEach(view -> {
						messageMediaDeleteEventStreamPublisher.publish(
							null, 
							view.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet()), 
							Stream.of(view.getFrom(), view.getTo())
								.filter(Objects::nonNull)
								.map(UUID::toString)
								.collect(Collectors.toSet()),
							null, 
							view.getMessageId().toString()
						).subscribe();
					});
				
				return messageRepository.deleteAllById(idsToDelete)
						.thenReturn(idsToDelete.size());
			})
			.reduce(0, Integer::sum);
	}

	/**
	 * Atomically releases the lock via Redis script execution.
	 */
	private Mono<Void> releaseLock(String lockValue) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class);
		
		return redisTemplate.execute(script, Collections.singletonList(LOCK_KEY), Collections.singletonList(lockValue))
			.next() // Get the first result element out of the Flux
			.doOnNext(released -> {
				if (Long.valueOf(1L).equals(released)) {
					log.info("Distributed lock safely released [Token: {}].", lockValue);
				} else {
					log.warn("Lock release bypassed: Lock lease expired or was overridden by another process context.");
				}
			})
			.doOnError(ex -> log.error("Failed to clean up lock key reference footprint from cache engine", ex))
			.then();
	}
}