package com.algomeet.xmpp.chatservice.scheduler;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.enums.CallSessionRedisKey;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.publisher.MissedCallStreamPublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <h2>MUC Missed Call Background Worker (Reactive)</h2>
 * <p>
 * This worker manages the lifecycle of unanswered Jingle (XEP-0166) sessions. 
 * It monitors a Redis Sorted Set for call timeouts and orchestrates missed call
 * delivery across the cluster.
 * </p>
 * * <h3>Key Reactive Principles applied:</h3>
 * <ul>
 * <li><b>Non-blocking I/O:</b> Uses {@code ReactiveRedisTemplate} to prevent Netty EventLoop saturation.</li>
 * <li><b>Thread Safety:</b> Employs {@code safeUnlock} to handle Redisson thread-affinity issues in asynchronous pipelines.</li>
 * <li><b>Context Isolation:</b> Manages {@code TenantContext} explicitly within elastic schedulers to support multi-tenancy.</li>
 * </ul>
 */
@Slf4j
@Component
@AllArgsConstructor
public class MucMissedCallScheduler {
	private final RedissonReactiveClient redissonReactiveClient;
	private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
	private final MissedCallStreamPublisher missedCallStreamPublisher;

	private final AtomicBoolean running = new AtomicBoolean(false);

	/**
	 * Main execution trigger. Subscribes to the reactive chain every 2 seconds.
	 * Using {@code fixedDelay} ensures that a new execution doesn't start until
	 * the previous reactive subscription has been initialized.
	 */
	@Scheduled(fixedDelay = 1000)
	public void processExpiredCalls() {
		if (!running.compareAndSet(false, true)) {
			return; // skip if previous run still executing
		}

		loadMissedCalls()
		.doFinally(sig -> running.set(false))
		.subscribe();
	}

	/**
	 * Scans for expired sessions and acquires a distributed lock to prevent multi-node processing.
	 * * @return A Mono signal indicating completion of the batch process.
	 */
	private Mono<Void> loadMissedCalls() {
		String lockKey = "xmpp:lock:publish:muc-missed-calls";
		RLockReactive lock = redissonReactiveClient.getLock(lockKey);

		return Mono.<Void, Boolean>usingWhen(
				// 1. ACQUIRE: Short wait time (300ms) with a safety lease (1s)
				lock.tryLock(300, 1000, TimeUnit.MILLISECONDS),
				acquired -> {
					if (!acquired) {
						return Mono.<Void>empty();
					}

					long now = System.currentTimeMillis();
					// 2. QUERY: Fetch all SIDs whose score (timeout) is <= now
					return reactiveRedisTemplate.opsForZSet()
							.rangeByScore(CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(), Range.closed(0.0, (double) now))
							.<String>flatMap(mucSid -> 
							// 3. ATOMIC REMOVE: Only the node that deletes the SID processes it
							reactiveRedisTemplate.opsForZSet()
							.remove(CallSessionRedisKey.MUC_CALL_TIMEOUT_QUEUE.getVal(), mucSid)							
							.filter(removed -> removed != null && removed > 0)
							.thenReturn(mucSid))
							.collectList() // Collects all successfully removed SIDs into a List<String>
							.<Void>flatMap(lists -> {								
								if(lists.isEmpty()) {
									return Mono.<Void>empty(); 
								}
								
								log.info("MUC {}  SID: {}", lists);
								
								// 3. Grouping (Synchronous, fast)
								Map<String, List<String>> groupedBySids = lists.stream()
								    .map(CallSessionRedisKey::getSidAndMucSidPair)
								    .collect(Collectors.groupingBy(
								        arr -> arr[0], 
								        Collectors.mapping(arr -> arr[1], Collectors.toList())
								    ));

								// 4. Processing (Reactive, non-blocking)
								return Flux.fromIterable(groupedBySids.entrySet())
								    .flatMap(entry -> 
								        missedCallStreamPublisher.publish(entry.getValue(), ChatType.GROUPCHAT.name())
								            .onErrorResume(e -> {
								                log.error("Failed to publish for SID: {}", entry.getKey(), e);
								                return Mono.empty(); 
								            })
								    )
								    .then(); // Returns Mono<Void>					
								    
							})
							.then();
				},
				// 5. CLEANUP: Safe unlock logic to prevent IllegalMonitorStateException crashes
				acquired -> acquired ? safeUnlock(lock) : Mono.empty(),
						(acquired, err) -> acquired ? safeUnlock(lock) : Mono.empty(),
								acquired -> acquired ? safeUnlock(lock) : Mono.empty()
				);
	}

	/**
	 * Handles Redisson's thread-id sensitivity. In reactive flows, the unlocking thread 
	 * may differ from the locking thread. This method catches ownership exceptions 
	 * to prevent breaking the reactive operator chain.
	 */
	private Mono<Void> safeUnlock(RLockReactive lock) {
		return lock.unlock()
				.onErrorResume(IllegalMonitorStateException.class, e -> {
					log.debug("Lock already released or ownership transferred: {}", e.getMessage());
					return Mono.empty();
				})
				.then();
	}	
}