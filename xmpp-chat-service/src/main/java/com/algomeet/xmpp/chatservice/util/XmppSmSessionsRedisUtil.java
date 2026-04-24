package com.algomeet.xmpp.chatservice.util;

import com.algomeet.xmpp.chatservice.properties.XmppSmRedisProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Redis utility for mapping:
 *
 * USER -> MANY STREAM MANAGEMENT SESSIONS
 *
 * Example:
 * --------------------------------------------------------
 * user123 may have:
 * - mobile session
 * - tablet session
 * - web browser session
 *
 * This component maintains that relationship so the server can:
 *
 * 1. Find all resumable sessions of a user
 * 2. Fan-out buffered messages to all sessions
 * 3. Clean expired session references automatically
 * 4. Support clustered XMPP deployments
 *
 * Design Strategy:
 * --------------------------------------------------------
 * ACTIVE CLEANUP:
 *   Redis key expiration pub/sub listener removes stale entries.
 *
 * PASSIVE CLEANUP:
 *   Read operations also verify existence and self-heal bad data.
 *
 * This dual strategy keeps indexes healthy even if Redis keyspace
 * notifications are temporarily unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSmSessionsRedisUtil {

	/**
	 * Reactive Redis template for non-blocking set operations.
	 */
	private final ReactiveStringRedisTemplate redis;

	/**
	 * Reactive Pub/Sub listener container.
	 *
	 * Used to subscribe to Redis key-expiration notifications.
	 */
	private final ReactiveRedisMessageListenerContainer listenerContainer;

	/**
	 * External configuration:
	 * - TTL values
	 * - SM redis settings
	 */
	private final XmppSmRedisProperties properties;

	/**
	 * Redis SET key containing all active SM sessions for a user.
	 *
	 * Example:
	 * xmpp:user:sessions:user123
	 */
	private static final String USER_SESSIONS_INDEX =
			"xmpp:user:sessions:%s";

	/**
	 * Redis keyspace notification channel pattern for expired keys.
	 *
	 * "__keyevent@*__:expired"
	 * means:
	 * any Redis DB index, expired event only.
	 */
	private static final String EXPIRED_EVENT_CHANNEL =
			"__keyevent@*__:expired";

	/**
	 * Lua script for ATOMIC READ + CLEAN.
	 *
	 * Problem:
	 * --------------------------------------------------------
	 * A user's session set may contain stale session IDs if:
	 * - app crashed before cleanup
	 * - Redis restart timing issues
	 * - missed expiration notification
	 *
	 * Solution:
	 * --------------------------------------------------------
	 * 1. Read all session IDs from SET
	 * 2. Check if backing SM session key still exists
	 * 3. Keep active ones
	 * 4. Remove stale ones immediately
	 *
	 * Result:
	 * Single atomic Redis round-trip.
	 */
	private static final String GET_AND_CLEAN_SESSIONS_LUA =
			"local user_set_key = KEYS[1] " +
					"local user_key = ARGV[1] " +
					"local sessions = redis.call('SMEMBERS', user_set_key) " +
					"local active_sessions = {} " +
					"for _, session_id in ipairs(sessions) do " +
					"  local session_key = '" + XmppSmSessionRedisUtil.SM_SESSION_KEY_PREFIX + "' .. user_key .. ':' .. session_id " +
					"  if redis.call('EXISTS', session_key) == 1 then " +
					"    table.insert(active_sessions, session_id) " +
					"  else " +
					"    redis.call('SREM', user_set_key, session_id) " +
					"  end " +
					"end " +
					"return active_sessions";

	/**
	 * Starts Redis expiration listener after Spring bean creation.
	 *
	 * Why @PostConstruct:
	 * --------------------------------------------------------
	 * Ensures dependencies are injected first, then listener starts.
	 *
	 * Listener Flow:
	 * --------------------------------------------------------
	 * Redis session key expires
	 * -> expiration event published
	 * -> receive event
	 * -> parse key
	 * -> remove stale session ID from user index
	 */
	@PostConstruct
	public void initExpirationListener() {

		/**
		 * Subscribe using pattern topic because Redis DB index may vary.
		 */
		PatternTopic expirationTopic =
				new PatternTopic(EXPIRED_EVENT_CHANNEL);

		listenerContainer.receive(expirationTopic)

		/**
		 * Extract raw expired key name from message envelope.
		 */
		.map(message -> message.getMessage())

		/**
		 * Ignore unrelated expired keys.
		 * Process only SM session keys.
		 */
		.filter(key ->
		key.startsWith(
				XmppSmSessionRedisUtil.SM_SESSION_KEY_PREFIX
				))

		/**
		 * Cleanup corresponding user index entry.
		 */
		.flatMap(this::handleAutoCleanup)

		/**
		 * Infinite resilience:
		 * If Redis connection drops,
		 * automatically retry forever every 10 seconds.
		 */
		.retryWhen(
				Retry.fixedDelay(
						Long.MAX_VALUE,
						Duration.ofSeconds(10)
						)
				)

		.subscribe(
				next -> {
					// no-op
				},
				err -> log.error("Critical error in Redis Expiration Listener", err),
				() -> log.warn("Redis Expiration Listener stream closed"));

		log.info("XMPP SM Session expiration listener started on pattern: {}", EXPIRED_EVENT_CHANNEL);
	}

	/**
	 * Handles one expired Redis SM session key.
	 *
	 * Expected key format:
	 * --------------------------------------------------------
	 * xmpp:sm:session:{userKey}:{sessionId}
	 *
	 * Example:
	 * xmpp:sm:session:user123:abc789
	 *
	 * @param expiredKey full expired Redis key
	 * @return completion signal
	 */
	private Mono<Void> handleAutoCleanup(String expiredKey) {

		try {
			/**
			 * Remove fixed prefix first.
			 *
			 * Remaining:
			 * user123:abc789
			 */
			String body = expiredKey.substring(
							XmppSmSessionRedisUtil.SM_SESSION_KEY_PREFIX.length()
							);

			/**
			 * First ":" separates userKey and sessionId.
			 */
			int delimiterIdx = body.indexOf(":");

			if (delimiterIdx > 0) {

				String userKey = body.substring(0, delimiterIdx);
				String sessionId = body.substring(delimiterIdx + 1);

				log.info("Real-time cleanup: Session {} expired for user {}", sessionId, userKey);

				/**
				 * Remove stale session reference from user's set.
				 */
				return removeSessionFromIndex(userKey, sessionId);
			}

		} catch (Exception e) {
			/**
			 * Never fail stream because of malformed key.
			 */
			log.warn("Failed to parse expired key: {}", expiredKey);
		}

		return Mono.empty();
	}

	/**
	 * Adds a Stream Management session to user's active set.
	 *
	 * Example:
	 * SADD xmpp:user:sessions:user123 sm-001
	 *
	 * Also refreshes index TTL so active users retain mapping.
	 *
	 * @param userKey owner user id
	 * @param smSessionId session id
	 * @return completion signal
	 */
	public Mono<Void> addSessionToIndex(
			String userKey,
			String smSessionId) {

		String key = formatIndexKey(userKey);

		return redis.opsForSet()

				/**
				 * Add session id into Redis SET.
				 */
				.add(key, smSessionId)

				/**
				 * Keep set alive while user active.
				 */
				.then(redis.expire(key, properties.getTtl()))

				/**
				 * Retry transient failures.
				 */
				.retryWhen(
						Retry.backoff(
								2,
								Duration.ofMillis(50)
								)
						)
				.then();
	}

	/**
	 * Removes one session id from user's set.
	 *
	 * Used when:
	 * - session expired
	 * - logout
	 * - disconnect cleanup
	 *
	 * @param userKey owner user id
	 * @param smSessionId session id
	 * @return completion signal
	 */
	public Mono<Void> removeSessionFromIndex(
			String userKey,
			String smSessionId) {

		return redis.opsForSet()
				.remove(
						formatIndexKey(userKey),
						smSessionId
						)
				.then();
	}

	/**
	 * Returns only ACTIVE + NON-EXPIRED session IDs.
	 *
	 * Uses Lua script so stale references are removed while reading.
	 *
	 * Example result:
	 * Flux:
	 * - sm-mobile
	 * - sm-web
	 *
	 * @param userKey owner user id
	 * @return active sessions
	 */
	@SuppressWarnings("unchecked")
	public Flux<String> getActiveNonExpiredSessions(String userKey) {

		String indexKey = formatIndexKey(userKey);

		RedisScript<List> script =
				new DefaultRedisScript<>(
						GET_AND_CLEAN_SESSIONS_LUA,
						List.class
						);

		return redis.execute(
				script,
				List.of(indexKey),
				List.of(userKey)
				)

				/**
				 * Redis returns one List object.
				 * Convert it into Flux<String>.
				 */
				 .flatMapIterable(list ->
				 (List<String>) list
						 )

				 .doOnError(e -> {
					 log.error("Atomic fetch failed for user: {}", userKey, e);
				 });
	}

	/**
	 * Formats user index key.
	 *
	 * Example:
	 * user123 ->
	 * xmpp:user:sessions:user123
	 */
	private String formatIndexKey(String userKey) {
		return String.format(USER_SESSIONS_INDEX, userKey);
	}
}