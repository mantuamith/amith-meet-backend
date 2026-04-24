package com.algomeet.xmpp.chatservice.session;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
@Component
public class UserSessionRegistry {
    private static final String USER_SESSIONS_KEY_PREFIX = "xmpp:user-sessions:";
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private final ObjectMapper objectMapper;

    // For zombie sessions cleanup
    @Value("${session.zombie-max-age-hours:168}") // Defaults to 7 days if missing
    private int zombieMaxAgeHours;
    
    public UserSessionRegistry(
            @Qualifier("stringRedisTemplate") RedisTemplate<String, String> redisTemplate,
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
     * processed by the {@code XmppUserGlobalPresenceHandler}.</p>
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
        
        // Cleanup zombie sessions
        Map<String, String> sessions = hashOps().entries(key);
        for (Map.Entry<String, String> session : sessions.entrySet()) {        	
            try {
            	// Deserialize
				UserSession userSession = objectMapper.readValue(session.getValue(), UserSession.class);
				long time = (Instant.now().toEpochMilli() - userSession.getUpdatedAt());
				// Convert to hours
				long sessionAgeInhours = time / (60 * 60 * 1000L);

				if (sessionAgeInhours > zombieMaxAgeHours) {
					hashOps().delete(key, session.getKey());
				}
			} catch (JsonProcessingException e) {
				log.error("Error transforming/processing user session from redis", e);
			} 
        }

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
    
    /**
     * Retrieves sessions for multiple users in a single Redis round-trip using Pipelining.
     * This is critical for performance when fetching presence for a user's contact list.
     * * @param userKeys List of unique user identifiers.
     * @return A Map where Key is the userKey and Value is the Set of their active UserSessions.
     */
    public Map<String, Set<UserSession>> getAllSessions(List<String> userKeys) {
        if (CollectionUtils.isEmpty(userKeys)) {
            return Collections.emptyMap();
        }

        try {
            // executePipelined will use the Template's StringSerializers automatically
            List<Object> pipelinedResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String userKey : userKeys) {
                    // Use the template's serializer to stay consistent with how data was saved
                    byte[] keyBytes = redisTemplate.getStringSerializer().serialize(userKey(userKey));
                    connection.hGetAll(keyBytes);
                }
                return null;
            });

            Map<String, Set<UserSession>> resultMap = new HashMap<>();

            for (int i = 0; i < userKeys.size(); i++) {
                String currentUserKey = userKeys.get(i);
                Object result = pipelinedResults.get(i);

                // FIX: Spring has already deserialized the bytes into Strings 
                // because your template is RedisTemplate<String, String>
                if (result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> hashEntries = (Map<Object, Object>) result;
                    Set<UserSession> sessions = new HashSet<>();

                    for (Object value : hashEntries.values()) {
                        if (value instanceof String json) {
                            try {
                                sessions.add(objectMapper.readValue(json, UserSession.class));
                            } catch (JsonProcessingException e) {
                                log.error("Failed to deserialize session JSON for user {}", currentUserKey, e);
                            }
                        }
                    }
                    resultMap.put(currentUserKey, sessions);
                } else {
                    resultMap.put(currentUserKey, new HashSet<>());
                }
            }

            return resultMap;

        } catch (Exception e) {
            log.error("Failed to execute pipelined session retrieval for {} keys", userKeys.size(), e);
            return Collections.emptyMap();
        }
    }
}