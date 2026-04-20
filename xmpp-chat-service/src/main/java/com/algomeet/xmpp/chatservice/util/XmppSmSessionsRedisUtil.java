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
 * Production-grade Utility for tracking User-to-SM-Session relationships.
 * Uses ReactiveRedisMessageListenerContainer for real-time auto-cleanup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppSmSessionsRedisUtil {	
    private final ReactiveStringRedisTemplate redis;
    private final ReactiveRedisMessageListenerContainer listenerContainer; // Required for Pub/Sub
    private final XmppSmRedisProperties properties;

    private static final String USER_SESSIONS_INDEX = "algomeet:user:sessions:%s";
    
    // The Redis channel for key expiration events
    private static final String EXPIRED_EVENT_CHANNEL = "__keyevent@*__:expired";

    /**
     * PASSIVE CLEANUP LUA: Handles data integrity during read operations.
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
     * ACTIVE CLEANUP: Listens for expiration events.
     */
    @PostConstruct
    public void initExpirationListener() {
        // Create the topic pattern
        PatternTopic expirationTopic = new PatternTopic(EXPIRED_EVENT_CHANNEL);

        // We explicitly use the Message interface from the Container
        listenerContainer.receive(expirationTopic)
                .map(message -> message.getMessage()) // message is of type ReactiveRedisMessageListenerContainer.Message<String, String>
                .filter(key -> key.startsWith(XmppSmSessionRedisUtil.SM_SESSION_KEY_PREFIX))
                .flatMap(this::handleAutoCleanup)
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(10)))
                .subscribe(
                    next -> {}, 
                    err -> log.error("Critical error in Redis Expiration Listener", err),
                    () -> log.warn("Redis Expiration Listener stream closed")
                );
        
        log.info("XMPP SM Session expiration listener started on pattern: {}", EXPIRED_EVENT_CHANNEL);
    }

    private Mono<Void> handleAutoCleanup(String expiredKey) {
        try {
            // Format: xmpp:sm:session:{userKey}:{sessionId}
            String body = expiredKey.substring(XmppSmSessionRedisUtil.SM_SESSION_KEY_PREFIX.length());
            int delimiterIdx = body.indexOf(":");
            
            if (delimiterIdx > 0) {
                String userKey = body.substring(0, delimiterIdx);
                String sessionId = body.substring(delimiterIdx + 1);
                
                log.info("Real-time cleanup: Session {} expired for user {}", sessionId, userKey);
                return removeSessionFromIndex(userKey, sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to parse expired key: {}", expiredKey);
        }
        return Mono.empty();
    }

    public Mono<Void> addSessionToIndex(String userKey, String smSessionId) {
        String key = formatIndexKey(userKey);
        return redis.opsForSet().add(key, smSessionId)
                .then(redis.expire(key, properties.getTtl()))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(50)))
                .then();
    }

    public Mono<Void> removeSessionFromIndex(String userKey, String smSessionId) {
        return redis.opsForSet().remove(formatIndexKey(userKey), smSessionId).then();
    }

    @SuppressWarnings("unchecked")
    public Flux<String> getActiveNonExpiredSessions(String userKey) {
        String indexKey = formatIndexKey(userKey);
        RedisScript<List> script = new DefaultRedisScript<>(GET_AND_CLEAN_SESSIONS_LUA, List.class);

        return redis.execute(script, List.of(indexKey), List.of(userKey))
                .flatMapIterable(list -> (List<String>) list) 
                .doOnError(e -> log.error("Atomic fetch failed for user: {}", userKey, e));
    }

    private String formatIndexKey(String userKey) {
        return String.format(USER_SESSIONS_INDEX, userKey);
    }
}