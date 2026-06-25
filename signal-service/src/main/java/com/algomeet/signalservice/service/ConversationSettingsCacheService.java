package com.algomeet.signalservice.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.common.service.AbstractConversationSettingsCache;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ConversationSettingsCacheService extends AbstractConversationSettingsCache{
    @Autowired
    private ConversationSettingsService conversationSettingsService;
	
    @Autowired
    private RedisTemplate<String, ConversationSettings> redisTemplate;
    
    public ConversationSettingsCacheService(ConversationSettingsService conversationSettingsService, RedisTemplate<String, ConversationSettings> redisTemplate) {
        this.conversationSettingsService = conversationSettingsService;
        this.redisTemplate = redisTemplate;
    }

    public ConversationSettings refreshSettingsCache(UUID userKey, UUID peerKey) {
        evictSettings(userKey, peerKey);
        return getCachedSettings(userKey, peerKey);
    }
    
    public ConversationSettings getCachedSettings(UUID userKey, UUID peerKey) {
        String conversationId = conversationSettingsService.getConversationId(userKey, peerKey);
        String key = getCacheKey(conversationId);

        try {
            Object cachedObj = redisTemplate.opsForValue().get(key);
            if (cachedObj != null) {
                // FIXED: Use .equals() because object references change during Redis deserialization
                if (EMPTY_SENTINEL.equals(cachedObj)) {
                    log.debug("Cache penetration match: Conversation ID {} flagged as non-existent.", conversationId);
                    return null;
                }
                log.debug("Cache hit for conversation ID: {}", conversationId);
                return (ConversationSettings) cachedObj;
            }
        } catch (Exception e) {
            log.error("Redis unreachable during lookup for conversation ID: {}. Falling back to direct client service invocation.", conversationId, e);
        }

        // FIXED: Lock only on the specific conversation ID context to prevent global thread blocking
        synchronized (conversationId.intern()) {
            try {
                Object secondaryCheck = redisTemplate.opsForValue().get(key);
                if (secondaryCheck != null) {
                    return EMPTY_SENTINEL.equals(secondaryCheck) ? null : (ConversationSettings) secondaryCheck;
                }
            } catch (Exception ignored) {}

            log.debug("Cache miss encountered. Fetching settings for conversation ID: {} from downstream service.", conversationId);
            ConversationSettings settings = null;
            try {
                settings = conversationSettingsService.getSettings(conversationId);
            } catch (Exception e) {
                log.error("Downstream service lookup failed critically for conversation ID: {}", conversationId, e);
                throw e;
            }

            try {
                if (settings != null) {
                    redisTemplate.opsForValue().set(key, settings, cacheTtl);
                } else {
                    // Cache the empty sentinel for 5 minutes to defend against database spamming
                    redisTemplate.opsForValue().set(key, EMPTY_SENTINEL, Duration.ofMinutes(5));
                }
            } catch (Exception e) {
                log.error("Failed to populate Redis cache for conversation ID: {}.", conversationId, e);
            }

            return settings;
        }
    }

    public void evictSettings(UUID userKey, UUID peerKey) {
        String conversationId = conversationSettingsService.getConversationId(userKey, peerKey);
        try {
            redisTemplate.delete(getCacheKey(conversationId));
            log.info("Evicted conversation settings for conversation ID: {} from cache successfully.", conversationId);
        } catch (Exception e) {
            log.error("Failed to evict conversation settings for conversation ID: {} from cache", conversationId, e);
        }
    }    
}
