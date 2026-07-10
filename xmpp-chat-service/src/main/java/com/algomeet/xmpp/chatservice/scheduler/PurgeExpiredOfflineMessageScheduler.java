package com.algomeet.xmpp.chatservice.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.beans.MessageSenderAndReceiver;
import com.algomeet.xmpp.chatservice.publisher.DeleteMessageMediaEventPublisher;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.service.UnreadCountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredOfflineMessageScheduler {

	private final OfflineMessageRepository messageRepository;
	private final DeleteMessageMediaEventPublisher messageMediaDeleteEventStreamPublisher;
	private final ReactiveStringRedisTemplate redisTemplate; 
	private final UnreadCountService unreadCountService;

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
		
		// Track lock acquisition safely across reactive operators
		AtomicBoolean lockAcquired = new AtomicBoolean(false);

		log.info("Attempting to acquire distributed lock for message purge...");

		// FIX: Wrap initialization inside Mono.defer to capture synchronous connection exceptions
		Mono.defer(() -> redisTemplate.opsForValue()
			.setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes))
			.flatMap(acquired -> {
				if (!Boolean.TRUE.equals(acquired)) {
					log.debug("Purge execution skipped: Another cluster node holds the scheduler lock key.");
					return Mono.empty();
				}

				lockAcquired.set(true);
				log.info("Distributed lock acquired successfully [Token: {}]. Starting message purge job...", lockValue);
				return executePurgePipeline();
			})
			// FIX: Extracted lock release to doFinally to completely eliminate lock leak exposures
			.doFinally(signalType -> {
				if (lockAcquired.get()) {
					releaseLock(lockValue)
						.subscribeOn(Schedulers.boundedElastic())
						.subscribe(null, err -> log.error("Background unlock task encountered a failure for token: {}", lockValue, err));
				}
			}))
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
				List<UUID> idsToDelete = new ArrayList<>();
				Set<MessageSenderAndReceiver> senderAndReceiverKeys = new HashSet<>();
				
				batch.forEach(m -> {
					idsToDelete.add(m.getStanzaId());
					if (m.getCountable() != null && m.getCountable()) {
						senderAndReceiverKeys.add(new MessageSenderAndReceiver(m.getFrom(), m.getTo()));
					}
				});

				log.debug("Purging a batch of {} expired messages", idsToDelete.size());

				// FIX: Removed dangerous detached loops (.subscribe inside forEach).
				// Structured media deletion stream into a bounded, backpressure-controlled execution chain.
				Flux<Object> mediaPublishEvents = Flux.fromIterable(batch)
					.filter(view -> view.getMediaIds() != null && !view.getMediaIds().isEmpty())
					.flatMap(view -> {
						Set<String> mediaIds = view.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet());
						Set<String> participantIds = Stream.of(view.getFrom(), view.getTo())
								.filter(Objects::nonNull)
								.map(UUID::toString)
								.collect(Collectors.toSet());

						return messageMediaDeleteEventStreamPublisher.publish(
							null, mediaIds, participantIds, null, view.getMessageId().toString()
						);
					}, 16); // Bounded execution concurrency threshold

				// FIX: Chain sequentially using .then() to guarantee events are sent to broker BEFORE records leave the DB
				return mediaPublishEvents
					.then(messageRepository.deleteAllById(idsToDelete))
					.then(Mono.defer(() -> {
						if (CollectionUtils.isEmpty(senderAndReceiverKeys)) {
							return Mono.<Void>empty();
						}
						
						return Flux.fromIterable(senderAndReceiverKeys)
								.flatMap(pair -> unreadCountService.syncUnreadCount(pair.sender(), pair.receiver()), 16)
								.then(); 
					}))
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