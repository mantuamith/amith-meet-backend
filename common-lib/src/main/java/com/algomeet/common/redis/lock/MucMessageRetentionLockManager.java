package com.algomeet.common.redis.lock;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MucMessageRetentionLockManager {
	@Autowired
	@Qualifier("commonStringRedisTemplate")
    private RedisTemplate<String, String> redisTemplate;
	
	private final static String LOCK_KEY_PREFIX = "common:lock:chat:update-retention:id:";

    private static final String RELEASE_LUA_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = 
            new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class);

    /**
     * Tries to acquire a lock. Returns a LockToken if successful, or null/empty if it fails.
     */
    public LockToken acquireLock(UUID groupId) {
    	Duration ttl = Duration.ofMinutes(10);
    	
    	return acquireLock(groupId, ttl);
    }
    
    public LockToken acquireLock(UUID groupId, Duration ttl) {
    	Duration lockTtl = (ttl != null) ? ttl : Duration.ofMinutes(10);
    	
    	String lockKey =  LOCK_KEY_PREFIX + groupId;
        String tokenValue = UUID.randomUUID().toString();
        
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, tokenValue, lockTtl);

        if (Boolean.TRUE.equals(acquired)) {
            return new LockToken(lockKey, tokenValue);
        }
        return null; 
    }
    
    /**
	 * Checks if a distributed lock is currently active for the given pair of users.
	 *
	 * @return true if the lock exists in Redis, false otherwise.
	 */
	public boolean isLocked(UUID groupId) {
		String lockKey = LOCK_KEY_PREFIX +  groupId;

		Boolean exists = redisTemplate.hasKey(lockKey);
		return Boolean.TRUE.equals(exists);
	}

    /**
     * Safely releases a distributed lock using its token wrapper.
     */
    public void releaseLock(LockToken token) {
        if (token == null || token.getLockKey() == null || token.getTokenValue() == null) {
            return;
        }
        try {
            redisTemplate.execute(
                    RELEASE_SCRIPT,
                    Collections.singletonList(token.getLockKey()),
                    token.getTokenValue()
            );
        } catch (Exception ex) {
            log.error("Failed to cleanly execute atomic release sequence for key: {}", token.getLockKey(), ex);
        }
    }

    /**
     * Immutable value object container representing an acquired lock's lease context.
     */
    public final static class LockToken {
        private final String lockKey;
        private final String tokenValue;

        public LockToken(String lockKey, String tokenValue) {
            this.lockKey = lockKey;
            this.tokenValue = tokenValue;
        }

        public String getLockKey() { return lockKey; }
        public String getTokenValue() { return tokenValue; }
    }
}