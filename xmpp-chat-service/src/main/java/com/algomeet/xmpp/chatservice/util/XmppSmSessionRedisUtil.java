package com.algomeet.xmpp.chatservice.util;

import com.algomeet.xmpp.chatservice.properties.StreamManagementProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Redis utility for XMPP Stream Management (XEP-0198) session state.
 *
 * Purpose:
 * ---------------------------------------------------------
 * Each SM-enabled client connection receives a resumable
 * session id. This utility stores the metadata needed to:
 *
 * 1. Resume a disconnected stream
 * 2. Track acknowledged stanza counters (h)
 * 3. Map logical SM session -> physical socket session
 * 4. Auto-expire abandoned sessions
 *
 * Why Redis:
 * ---------------------------------------------------------
 * - Shared across clustered nodes
 * - Fast access for reconnect/resume flows
 * - Native TTL support
 * - Suitable for temporary session state
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSmSessionRedisUtil {

	/**
	 * Reactive Redis template for non-blocking operations.
	 *
	 * Used because the application stack is Reactor-based
	 * and should avoid blocking I/O threads.
	 */
	private final ReactiveStringRedisTemplate redis;

	/**
	 * Externalized configuration:
	 * - TTL duration
	 * - SM-related Redis settings
	 */
	private final StreamManagementProperties properties;

	/**
	 * Redis key pattern for per-session SM state.
	 *
	 * Example:
	 * xmpp:sm:user:session:abc123
	 */
	public static final String SM_SESSION_KEY = "xmpp:sm:user:session:%s";

	/**
	 * Prefix used for scanning / indexing if needed.
	 */
	public static final String SM_SESSION_KEY_PREFIX = "xmpp:sm:user:session:";

	/**
	 * Hash field storing latest inbound acknowledged counter.
	 *
	 * In XEP-0198, "h" represents stanza count acknowledged.
	 */
	public static final String FIELD_H = "h";

	/**
	 * Hash field storing current transport/application session id.
	 *
	 * This changes during resume when socket connection changes.
	 */
	public static final String FIELD_USER_SESSION_ID = "userSessionId";

	/**
	 * LUA script for atomic save:
	 *
	 * Why script is needed:
	 * ---------------------------------------------------------
	 * Without Lua:
	 *   HMSET
	 *   EXPIRE
	 *
	 * If application crashes between these two commands,
	 * a permanent zombie key may remain.
	 *
	 * With Lua:
	 * Both happen atomically in one Redis execution unit.
	 */
	private static final String SAVE_LUA =
			"redis.call('HMSET', KEYS[1], '" + FIELD_H + "', ARGV[2], '" + FIELD_USER_SESSION_ID + "', ARGV[3]) " +
					"redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
					"return 1";

	/**
	 * Saves full Stream Management session state atomically.
	 *
	 * Stores:
	 * - latest h counter
	 * - mapped userSessionId
	 * - TTL expiration
	 *
	 * Used when:
	 * - client enables SM
	 * - server creates resumable state
	 *
	 * @param smSessionId SM session id
	 * @param h last known acknowledged counter
	 * @param userSessionId current connection/session id
	 * @return completion signal
	 */
	public Mono<Void> saveSessionState(
			String smSessionId,
			long h,
			String userSessionId) {

		String key = key(SM_SESSION_KEY, smSessionId);

		RedisScript<Long> script =
				new DefaultRedisScript<>(SAVE_LUA, Long.class);

		return redis.execute(
				script,
				List.of(key),
				List.of(
						String.valueOf(properties.getSession().getResumeTtl().getSeconds()),
						String.valueOf(h),
						userSessionId
						)
				)

				/**
				 * We only care that it completed successfully.
				 */
				.then()
				.doOnError(e ->
				log.error(
						"Atomic session save failed: {}",
						smSessionId,
						e
						)
						);
	}

	/**
	 * Updates physical transport session mapping.
	 *
	 * Important during <resume/> flow:
	 *
	 * Old socket closes:
	 *   userSessionId = socket-A
	 *
	 * New socket reconnects:
	 *   userSessionId = socket-B
	 *
	 * Same SM session continues, only transport changes.
	 *
	 * TTL is refreshed because resumed session is active again.
	 *
	 * @param smSessionId SM session id
	 * @param newUserSessionId new socket/app session id
	 * @return true if field updated and ttl refreshed
	 */
	public Mono<Boolean> updateUserSessionId(
			String smSessionId,
			String newUserSessionId) {

		String key = key(SM_SESSION_KEY, smSessionId);

		return redis.opsForHash()

				/**
				 * Replace old mapped connection id.
				 */
				.put(key, FIELD_USER_SESSION_ID, newUserSessionId)

				/**
				 * Refresh expiration.
				 */
				.flatMap(success ->	redis.expire(key, properties.getSession().getResumeTtl()))

				/**
				 * Retry quick transient issues.
				 */
				.retryWhen(Retry.backoff(2, Duration.ofMillis(50)))

				.doOnError(e -> {
					log.error("Failed to update userSessionId for session: {}", smSessionId, e);
				});
	}

	/**
	 * Retrieves all fields of an SM session hash.
	 *
	 * Example returned map:
	 * {
	 *   h=25,
	 *   userSessionId=socket-xyz
	 * }
	 *
	 * Useful for:
	 * - resume diagnostics
	 * - session restore logic
	 * - debugging
	 *
	 * @param smSessionId SM session id
	 * @return map of session fields
	 */
	public Mono<Map<String, String>> getSmSessionData(String smSessionId) {

		return redis.opsForHash()

				/**
				 * entries() returns Flux<Map.Entry>.
				 * Convert into Mono<Map>.
				 */
				.entries(key(SM_SESSION_KEY, smSessionId))

				.collectMap(
						entry -> entry.getKey().toString(),
						entry -> entry.getValue().toString()
						)

				/**
				 * If session does not exist, return empty map.
				 */
				.defaultIfEmpty(Collections.emptyMap())

				.doOnError(e -> {
					log.error("Failed to retrieve SM Map for session: {}", smSessionId, e);
				});
	}

	/**
	 * Deletes Stream Management session state.
	 *
	 * Used when:
	 * - client disables SM
	 * - resume window expired
	 * - logout
	 * - cleanup after unrecoverable disconnect
	 *
	 * @param smSessionId SM session id
	 * @return completion signal
	 */
	public Mono<Void> deleteSession(String smSessionId) {
		return redis.delete(key(SM_SESSION_KEY, smSessionId))

				.doOnSuccess(v -> {
					log.debug("Deleted SM session: {}",	smSessionId);
				})
				.then();
	}

	/**
	 * Builds formatted Redis key from pattern.
	 *
	 * Example:
	 * key("xmpp:sm:user:session:%s", "abc")
	 * -> xmpp:sm:user:session:abc
	 */
	private String key(String pattern, String smSessionId) {
		return String.format(pattern, smSessionId);
	}	
}