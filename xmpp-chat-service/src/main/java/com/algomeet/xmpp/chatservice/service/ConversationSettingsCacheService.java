package com.algomeet.xmpp.chatservice.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.common.service.AbstractConversationSettingsCache;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service // Registers this implementation as a Spring Bean
public class ConversationSettingsCacheService extends AbstractConversationSettingsCache {

    @Autowired
    private ReactiveConversationSettingsService conversationSettingsService;
	
    @Autowired
    private ReactiveRedisTemplate<String, ConversationSettings> redisTemplate;
    
    public ConversationSettingsCacheService(ReactiveConversationSettingsService conversationSettingsService, 
                                                 ReactiveRedisTemplate<String, ConversationSettings> redisTemplate) {
        this.conversationSettingsService = conversationSettingsService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Evicts the target conversation settings and yields the fresh document downstream.
     */
    public Mono<ConversationSettings> refreshSettingsCache(UUID userKey, UUID peerKey) {
        return evictSettings(userKey, peerKey)
                .then(Mono.defer(() -> getCachedSettings(userKey, peerKey)));
    }
    
    /**
     * Resolves settings reactively via Cache-Aside strategy.
     */
    public Mono<ConversationSettings> getCachedSettings(UUID userKey, UUID peerKey) {
        String conversationId = conversationSettingsService.getConversationId(userKey, peerKey);
        String key = getCacheKey(conversationId);

        return redisTemplate.opsForValue().get(key)
                // --- Layer 1: Cache Lookup Handling ---
                .map(cachedObj -> {
                    if (EMPTY_SENTINEL.equals(cachedObj)) {
                        log.debug("Cache penetration match: Conversation ID {} flagged as non-existent.", conversationId);
                        // Wrap in a custom empty state carrier or handle transparently
                        return EMPTY_SENTINEL; 
                    }
                    log.debug("Cache hit for conversation ID: {}", conversationId);
                    return (ConversationSettings) cachedObj;
                })
                // Log and bypass Redis transient lookup failures without crashing the application pipeline
                .onErrorResume(e -> {
                    log.error("Redis unreachable during lookup for conversation ID: {}. Falling back to DB stream directly.", conversationId, e);
                    return Mono.empty();
                })
                // --- Layer 2: Cache Miss Handling (Database Fetch + Cache Populate) ---
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss encountered. Fetching settings for conversation ID: {} from downstream database.", conversationId);
                    
                    return conversationSettingsService.getSettings(conversationId)
                            // If DB returns a document, write to Redis with standard TTL
                            .flatMap(settings -> redisTemplate.opsForValue().set(key, settings, cacheTtl)
                                    .thenReturn(settings))
                            // If DB returns nothing (null/empty), cache the sentinel wrapper to block cache-penetration attacks
                            .switchIfEmpty(Mono.defer(() -> 
                                    redisTemplate.opsForValue().set(key, EMPTY_SENTINEL, Duration.ofMinutes(5))
                                            .thenReturn(EMPTY_SENTINEL)
                            ));
                }))
                // --- Layer 3: Final Output Mapping ---
                .flatMap(result -> EMPTY_SENTINEL.equals(result) ? Mono.empty() : Mono.just(result));
    }

    /**
     * Clears the target key out of Redis asynchronously.
     */
    public Mono<Void> evictSettings(UUID userKey, UUID peerKey) {
        String conversationId = conversationSettingsService.getConversationId(userKey, peerKey);
        String key = getCacheKey(conversationId);
        
        return redisTemplate.delete(key)
                .doOnSuccess(deletedCount -> log.info("Evicted conversation settings for conversation ID: {} from cache successfully.", conversationId))
                .doOnError(e -> log.error("Failed to evict conversation settings for conversation ID: {} from cache", conversationId, e))
                .then();
    }      
}