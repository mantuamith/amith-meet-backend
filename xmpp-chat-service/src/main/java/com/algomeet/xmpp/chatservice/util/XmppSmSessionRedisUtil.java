package com.algomeet.xmpp.chatservice.util;

import com.algomeet.xmpp.chatservice.properties.XmppSmRedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSmSessionRedisUtil {

    private final ReactiveStringRedisTemplate redis;
    private final XmppSmRedisProperties properties;

    public static final String SM_SESSION_KEY = "xmpp:sm:session:%s";
    public static final String SM_SESSION_KEY_PREFIX = "xmpp:sm:session:";
    
    private static final String FIELD_H = "h";
    private static final String FIELD_USER_SESSION_ID = "userSessionId";

    /**
     * LUA Script: Atomically saves data and sets expiration.
     * This prevents 'zombie keys' if the application crashes mid-execution.
     */
    private static final String SAVE_LUA = 
        "redis.call('HMSET', KEYS[1], '" + FIELD_H + "', ARGV[2], '" + FIELD_USER_SESSION_ID + "', ARGV[3]) " +
        "redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
        "return 1";

    public Mono<Long> incrementH(String smSessionId) {
        String key = key(SM_SESSION_KEY, smSessionId);
        return redis.opsForHash()
                .increment(key, FIELD_H, 1L)
                .flatMap(val -> redis.expire(key, properties.getTtl()).thenReturn(val))
                // Production Resilience: Retry on brief network blips
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))) 
                .doOnError(e -> log.error("Failed to increment SM counter for session: {}", smSessionId, e));
    }

    public Mono<Void> saveSessionState(String smSessionId, long h, String userSessionId) {
        String key = key(SM_SESSION_KEY, smSessionId);
        RedisScript<Long> script = new DefaultRedisScript<>(SAVE_LUA, Long.class);

        // Atomic operation: HMSET + EXPIRE in one network round-trip
        return redis.execute(script, 
                    List.of(key), 
                    List.of(String.valueOf(properties.getTtl().getSeconds()), String.valueOf(h), userSessionId))
                .then()
                .doOnError(e -> log.error("Atomic session save failed: {}", smSessionId, e));
    }
    
    /**
     * Updates the physical User Session ID in Redis and refreshes the TTL.
     * Used during <resume/> to map the stream to the new connection context.
     */
    public Mono<Boolean> updateUserSessionId(String smSessionId, String newUserSessionId) {
        String key = key(SM_SESSION_KEY, smSessionId);
        return redis.opsForHash()
                .put(key, FIELD_USER_SESSION_ID, newUserSessionId)
                .flatMap(success -> redis.expire(key, properties.getTtl()))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(50)))
                .doOnError(e -> log.error("Failed to update userSessionId for session: {}", smSessionId, e));
    }
    
    public Mono<Long> getLastAck(String smSessionId) {
        return redis.opsForHash()
                .get(key(SM_SESSION_KEY, smSessionId), FIELD_H)
                .map(this::safeParseLong)
                .defaultIfEmpty(0L);
    }

    public Mono<Void> deleteSession(String smSessionId) {
        return redis.delete(key(SM_SESSION_KEY, smSessionId))
                .doOnSuccess(v -> log.debug("Deleted SM session: {}", smSessionId))
                .then();
    }

    private String key(String pattern, String smSessionId) {
        return String.format(pattern, smSessionId);
    }

    // Defensive parsing to prevent reactive stream crashes
    private long safeParseLong(Object val) {
        try {
            return val != null ? Long.parseLong(val.toString()) : 0L;
        } catch (NumberFormatException e) {
            log.warn("Corrupt counter in Redis for session. Value: {}", val);
            return 0L;
        }
    }
}