package com.algomeet.xmpp.chatservice.session;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserSessionRegistry {
    private static final String USER_SESSION_KEY_PREFIX = "chat-user-session:";
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private String userKey(String userId) {
        return USER_SESSION_KEY_PREFIX + userId;
    }
    
    private HashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    // ADD (atomic)
    public void addSession(String userId, UserSession session) {
        try {
            String key = userKey(userId);
            String value = objectMapper.writeValueAsString(session);
            hashOps().put(key, session.getSessionId(), value);

        } catch (Exception e) {
            log.error("Failed to add session", e);
        }
    }

    // GET ALL
    public Set<UserSession> getSessions(String userId) {
        try {
            String key = userKey(userId);

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
            log.error("Failed to get sessions", e);
            return new HashSet<>();
        }
    }

    // REMOVE ONE (atomic)
    public void removeSession(String userId, String sessionId) {
        String key = userKey(userId);
        hashOps().delete(key, sessionId);

        // Optional cleanup if empty
        Long size = hashOps().size(key);
        if (size == null || size == 0) {
            redisTemplate.delete(key);
        }
    }

    // CHECK EXISTS
    public boolean hasSession(String userId, String sessionId) {
        String key = userKey(userId);
        return hashOps().hasKey(key, sessionId);
    }

    // DELETE ALL
    public void deleteAllSessions(String userId) {
        redisTemplate.delete(userKey(userId));
    }
}