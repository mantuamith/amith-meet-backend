package com.algomeet.chatservice.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.chatservice.dto.SessionMetadata;

@Service
public class UserSessionService {
	private final String USER_SESSION_REDIS_KEY_PREFIX = "chat-user-session:";

    private final RedisTemplate<String, Set<SessionMetadata>> redisTemplate;

    public UserSessionService(RedisTemplate<String, Set<SessionMetadata>> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String redisKey(String userId) {
        return USER_SESSION_REDIS_KEY_PREFIX + userId;
    }

    public void addSession(String userId, String sessionId, String userKey) {
        String key = redisKey(userId);

        SessionMetadata session = SessionMetadata.builder()
        		.sessionId(sessionId)
        		.isActive(true)
        		.userKey(userKey)
        		.build();

        Set<SessionMetadata> sessions = redisTemplate.opsForValue().get(key);
        if (sessions == null) {
            sessions = new HashSet<>();
        }

        sessions.add(session);
        redisTemplate.opsForValue().set(key, sessions);
    }

    /** Returns the userKey (UUID) for the given username, or null if not found / not connected. */
    public String getUserKey(String username) {
        Set<SessionMetadata> sessions = getSessions(username);
        if (CollectionUtils.isEmpty(sessions)) return null;
        return sessions.stream()
                .filter(s -> s.getUserKey() != null)
                .map(SessionMetadata::getUserKey)
                .findFirst()
                .orElse(null);
    }

    public Set<SessionMetadata> getSessions(String userId) {
        return redisTemplate.opsForValue().get(redisKey(userId));
    }
    
    public void updateSessions(String userId, Set<SessionMetadata> sessions) {
    	redisTemplate.opsForValue().set(redisKey(userId), sessions);
    }

    public void removeSession(String userId, String sessionId) {
        String key = redisKey(userId);

        Set<SessionMetadata> sessions = redisTemplate.opsForValue().get(key);
        if (sessions != null) {
            sessions.removeIf(s -> s.getSessionId().equals(sessionId));
            redisTemplate.opsForValue().set(key, sessions);
        } 
        
        // Check if there is still value in the sessions
        sessions = redisTemplate.opsForValue().get(key);
        if (CollectionUtils.isEmpty(sessions)) {
        	// If sessions is empty remove the redis entry
        	redisTemplate.delete(key);
        }
    }

    public void deleteAllSessions(String userId) {
        redisTemplate.delete(redisKey(userId));
    }
}