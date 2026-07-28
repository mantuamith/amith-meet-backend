package com.algomeet.xmpp.chatservice.session;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <p>A distributed registry for managing active XMPP user sessions using Redis.</p>
 * 
 * <p>This repository stores session metadata in Redis Hashes, where:</p>
 * <ul>
 *     <li><b>Key:</b> {@code chat-user-sessions:{userKey}} (The unique identifier for a user).</li>
 *     <li><b>Field:</b> {@code sessionId} (The unique Netty Channel ID).</li>
 *     <li><b>Value:</b> A serialized {@link UserSession} JSON object containing state, 
 *         IP, and timestamps.</li>
 * </ul>
 * 
 * <p>By using a centralized Redis store, this registry enables features like:</p>
 * <ul>
 *     <li><b>Multi-device support:</b> Tracking multiple concurrent connections for one user.</li>
 *     <li><b>Cluster awareness:</b> Allowing Node A to find a session managed by Node B.</li>
 *     <li><b>Presence tracking:</b> Real-time updates of user states (ACTIVE, AWAY, DND).</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
public class UserSessionRegistry {
    private static final String USER_SESSIONS_KEY_PREFIX = "xmpp:user-sessions:";
    
    private final ReactiveStringRedisTemplate redisTemplate;
    
    private final ObjectMapper objectMapper;

    // For zombie sessions cleanup
    @Value("${session.zombie-max-age-hours:168}") // Defaults to 7 days if missing
    private int zombieMaxAgeHours;
    
    public UserSessionRegistry(
            @Qualifier("reactiveStringRedisTemplate") ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Generates the Redis key for a specific user's session hash.
     */
    private String userKey(String userKey) {
        return USER_SESSIONS_KEY_PREFIX + userKey;
    }
    
    /**
     * Helper to access Redis Hash operations.
     */
    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    /**
     * Registers a new session in the distributed store.
     * 
     * @param userKey The unique key of the user.
     * @param session The session metadata to persist.
     */
    public Mono<Boolean> addSession(String userKey, UserSession session) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(session))
                .subscribeOn(Schedulers.parallel()) // <-- Run CPU-bound JSON serialization here
                .flatMap(value -> {
                    String key = userKey(userKey);
                    return hashOps().put(key, session.getSessionId(), value);
                })
                .doOnSuccess(success -> log.debug("Session {} added for user {}", session.getSessionId(), userKey))
                .doOnError(e -> log.error("CRITICAL: Failed to persist session to Redis for user {}", userKey, e))
                .onErrorReturn(false);
    }

    /**
     * Retrieves all active sessions for a given user across the entire cluster.
     * 
     * @param userKey The unique key of the user.
     * @return A Set of {@link UserSession} objects, or an empty Set if none exist.
     */
    public Mono<Set<UserSession>> getSessions(String userKey) {
        String key = userKey(userKey);

        return hashOps().values(key)
                .map(value -> {
                    try {
                        return objectMapper.readValue(value, UserSession.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize session JSON for user {}", userKey, e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collectList()
                .map(list -> (Set<UserSession>) new HashSet<>(list)) 
                .onErrorResume(e -> {
                    log.error("Failed to retrieve sessions from Redis for user {}", userKey, e);
                    return Mono.just(new HashSet<>());
                });
    }
    
    /**
     * Updates the availability status and timestamp of a specific session.
     * 
     * <p>This is typically triggered by XMPP Presence or Chat State stanzas 
     * processed by the {@code XmppUserGlobalPresenceHandler}.</p>
     * 
     * @param userKey    The user's unique key.
     * @param sessionId The specific Netty channel ID.
     * @param newState  The new {@link UserState} (e.g., ACTIVE, AWAY, GONE).
     */
    public Mono<Void> updateSessionStatus(String userKey, String sessionId, UserState newState) {
        String key = userKey(userKey);
        
        // 1. Retrieve the existing session JSON from the Redis Hash
        return hashOps().get(key, sessionId)
                .flatMap(json -> {
                    try {
                        // 2. Deserialize, update fields, and re-serialize
                        UserSession session = objectMapper.readValue(json, UserSession.class);
                        session.setState(newState);
                        
                        // 3. Set update timestamp (UTC)
                        session.setUpdatedAt(Instant.now().toEpochMilli());

                        String updatedValue = objectMapper.writeValueAsString(session);
                        
                        // 4. Persist back to Redis
                        return hashOps().put(key, sessionId, updatedValue)
                                .doOnSuccess(v -> log.debug("Session {} status updated to {} for user {}", sessionId, newState, userKey))
                                .then();
                    } catch (Exception e) {
                        log.error("Failed to update session status serialization for user: {}", userKey, e);
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.fromRunnable(() -> 
                    log.warn("Cannot update status: Session {} not found for user {}", sessionId, userKey)
                ))
                .onErrorResume(e -> {
                    log.error("Failed to update session status for user: {}", userKey, e);
                    return Mono.empty();
                });
    }
    
    /**
     * Removes a specific session (e.g., on WebSocket disconnect). 
     * If no sessions remain for the user, the root key is cleaned up.
     * 
     * @param userKey   The user's unique key.
     * @param sessionId The ID of the session to terminate.
     */
    public Mono<Void> removeSession(String userKey, String sessionId) {
        String key = userKey(userKey);
        
        // FIX: Use .remove() to delete specific fields/sessionIds from the Hash
        return hashOps().remove(key, sessionId)
                .thenMany(hashOps().entries(key)) // Treat entries as a stream (Flux)
                .flatMap(entry -> {
                    try {
                        // Deserialize
                        UserSession userSession = objectMapper.readValue(entry.getValue(), UserSession.class);
                        long time = (Instant.now().toEpochMilli() - userSession.getUpdatedAt());
                        // Convert to hours
                        long sessionAgeInhours = time / (60 * 60 * 1000L);

                        if (sessionAgeInhours > zombieMaxAgeHours) {
                            // FIX: Use .remove() here as well for field deletion
                            return hashOps().remove(key, entry.getKey()).then();
                        }
                    } catch (JsonProcessingException e) {
                        log.error("Error transforming/processing user session from redis", e);
                    }
                    return Mono.empty();
                })
                // Complete processing of all entries before evaluating root key size
                .then(hashOps().size(key)) 
                .flatMap(size -> {
                    // Cleanup: If the user has no more active sessions, remove the hash key entirely
                    if (size == null || size == 0) {
                        // Correct use of redisTemplate.delete(key) to wipe out the whole root key
                        return redisTemplate.delete(key)
                                .doOnSuccess(v -> log.debug("All sessions removed. Cleaned up root key for user: {}", userKey))
                                .then();
                    }
                    return Mono.empty();
                });
    }

    /**
     * Validates if a specific session ID is still present in the global registry.
     */
    public Mono<Boolean> hasSession(String userKey, String sessionId) {
        String key = userKey(userKey);
        return hashOps().hasKey(key, sessionId);
    }

    /**
     * Forcefully clears all session data for a user.
     */
    public Mono<Void> deleteAllSessions(String userKey) {
        return redisTemplate.delete(userKey(userKey))
                .doOnSuccess(v -> log.info("Force-deleted all sessions for user: {}", userKey))
                .then();
    }
    
    /**
     * Retrieves sessions for multiple users in a single Redis round-trip without blocking threads.
     * This uses non-blocking concurrent streams which replaces manual pipelining.
     * 
     * @param userKeys List of unique user identifiers.
     * @return A Map where Key is the userKey and Value is the Set of their active UserSessions.
     */
    public Mono<Map<String, Set<UserSession>>> getAllSessions(List<String> userKeys) {
        if (CollectionUtils.isEmpty(userKeys)) {
            return Mono.just(Collections.emptyMap());
        }

        return Flux.fromIterable(userKeys)
                .flatMap(currentUserKey -> {
                    String key = userKey(currentUserKey);
                    
                    return hashOps().values(key)
                            .map(json -> {
                                try {
                                    return objectMapper.readValue(json, UserSession.class);
                                } catch (JsonProcessingException e) {
                                    log.error("Failed to deserialize session JSON for user {}", currentUserKey, e);
                                    return null; // Handled by filter below
                                }
                            })
                            .filter(java.util.Objects::nonNull)
                            .collectList()
                            .map(HashSet::new) // Collect to standard Set
                            // Transform item stream into an entry object linking userKey with their sessions
                            .map(sessions -> Map.entry(currentUserKey, (Set<UserSession>) sessions))
                            // Handle exceptions isolated to an individual user lookup to avoid collapsing the batch
                            .onErrorReturn(Map.entry(currentUserKey, new HashSet<>()));
                })
                // Reduce the streamed map entries back into your unified result Map
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .onErrorResume(e -> {
                    log.error("Failed to execute reactive session retrieval for {} keys", userKeys.size(), e);
                    return Mono.just(Collections.emptyMap());
                });
    }
}