package com.algomeet.xmpp.chatservice.util;

import java.time.Duration;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.properties.XmppSmRedisProperties;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Utility class for managing XEP-0198 Stream Management counters in Redis.
 *
 * Responsibilities:
 * - Store inbound sequence counter (h)
 * - Store last acknowledged counter (h)
 * - Retrieve stored values for resume
 *
 * NOTE:
 * - No increment logic (handled in-memory or sequencer)
 * - Redis acts as durable storage for resume / clustering
 */
@Component
@RequiredArgsConstructor
public class XmppSmRedisUtil {

    private final ReactiveStringRedisTemplate redis;
    private final XmppSmRedisProperties xmppSmRedisProperties;

    private static final String LAST_ACK_KEY = "xmpp:sm:lastAck:%s";
  
    /**
     * Save last acknowledged counter (h).
     */
    public Mono<Void> saveLastAck(String sessionId, long h) {
        return redis.opsForValue()
                .set(key(LAST_ACK_KEY, sessionId), String.valueOf(h), xmppSmRedisProperties.getTtl())
                .then();
    }

    /**
     * Retrieve last acknowledged counter (h).
     */
    public Mono<Long> getLastAck(String sessionId) {
        return redis.opsForValue()
                .get(key(LAST_ACK_KEY, sessionId))
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    /**
     * Delete session state (e.g., logout or resume failure).
     */
    public Mono<Void> deleteSession(String sessionId) {
        return redis.delete(
                key(LAST_ACK_KEY, sessionId)
        ).then();
    }

    /**
     * Format Redis key.
     */
    private String key(String pattern, String sessionId) {
        return String.format(pattern, sessionId);
    }
}