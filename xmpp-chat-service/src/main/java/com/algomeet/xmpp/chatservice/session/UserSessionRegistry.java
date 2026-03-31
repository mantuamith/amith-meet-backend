package com.algomeet.xmpp.chatservice.session;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.enums.UserState;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Repository
@RequiredArgsConstructor
public class UserSessionRegistry {

    private static final String USER_SESSIONS_KEY_PREFIX = "chat-user-sessions:";
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Generates the Redis key for a specific user's session hash.
     */
    private String userKey(String userKey) {
        return USER_SESSIONS_KEY_PREFIX + userKey;
    }
    
    /**
     * Helper to access Redis Hash operations.
     */
    private HashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    /**
     * Registers a new session in the distributed store.
     * 
     * @param userKey The unique key of the user.
     * @param session The session metadata to persist.
     */
    public void addSession(String userKey, UserSession session) {
        try {
            String key = userKey(userKey);
            String value = objectMapper.writeValueAsString(session);
            hashOps().put(key, session.getSessionId(), value);
            log.debug("Session {} added for user {}", session.getSessionId(), userKey);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to persist session to Redis for user {}", userKey, e);
        }
    }

    /**
     * Retrieves all active sessions for a given user across the entire cluster.
     * 
     * @param userKey The unique key of the user.
     * @return A Set of {@link UserSession} objects, or an empty Set if none exist.
     */
    public Set<UserSession> getSessions(String userKey) {
        try {
            String key = userKey(userKey);

            Map<String, String> entries = hashOps().entries(key);
            if (CollectionUtils.isEmpty(entries)) {
                return new HashSet<>();
            }

            Set<UserSession> sessions = new HashSet<>();
            for (String value : entries.values()) {
                sessions.add(objectMapper.readValue(value, UserSession.class));
            }

            return sessions;
        } catch (Exception e) {
            log.error("Failed to retrieve sessions from Redis for user {}", userKey, e);
            return new HashSet<>();
        }
    }
    
    /**
     * Updates the availability status and timestamp of a specific session.
     * 
     * <p>This is typically triggered by XMPP Presence or Chat State stanzas 
     * processed by the {@code XmppSessionLifecycleHandler}.</p>
     * 
     * @param userKey    The user's unique key.
     * @param sessionId The specific Netty channel ID.
     * @param newState  The new {@link UserState} (e.g., ACTIVE, AWAY, GONE).
     */
    public void updateSessionStatus(String userKey, String sessionId, UserState newState) {
        try {
            String key = userKey(userKey);
            
            // 1. Retrieve the existing session JSON from the Redis Hash
            String json = hashOps().get(key, sessionId);
            if (json == null) {
                log.warn("Cannot update status: Session {} not found for user {}", sessionId, userKey);
                return;
            }

            // 2. Deserialize, update fields, and re-serialize
            UserSession session = objectMapper.readValue(json, UserSession.class);
            session.setState(newState);
            
            // 3. Set update timestamp (UTC)
            session.setUpdatedAt(Instant.now().toEpochMilli());

            String updatedValue = objectMapper.writeValueAsString(session);
            
            // 4. Persist back to Redis
            hashOps().put(key, sessionId, updatedValue);
            
            log.debug("Session {} status updated to {} for user {}", sessionId, newState, userKey);

        } catch (Exception e) {
            log.error("Failed to update session status for user: {}", userKey, e);
        }
    }
    
    /**
     * Removes a specific session (e.g., on WebSocket disconnect). 
     * If no sessions remain for the user, the root key is cleaned up.
     * 
     * @param userKey   The user's unique key.
     * @param sessionId The ID of the session to terminate.
     */
    public void removeSession(String userKey, String sessionId) {
        String key = userKey(userKey);
        hashOps().delete(key, sessionId);

        // Cleanup: If the user has no more active sessions, remove the hash key entirely
        Long size = hashOps().size(key);
        if (size == null || size == 0) {
            redisTemplate.delete(key);
            log.debug("All sessions removed. Cleaned up root key for user: {}", userKey);
        }
    }

    /**
     * Validates if a specific session ID is still present in the global registry.
     */
    public boolean hasSession(String userKey, String sessionId) {
        String key = userKey(userKey);
        return hashOps().hasKey(key, sessionId);
    }

    /**
     * Forcefully clears all session data for a user.
     */
    public void deleteAllSessions(String userKey) {
        redisTemplate.delete(userKey(userKey));
        log.info("Force-deleted all sessions for user: {}", userKey);
    }
}