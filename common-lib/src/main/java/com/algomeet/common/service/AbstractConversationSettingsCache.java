package com.algomeet.common.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;

import com.algomeet.common.dto.ConversationSettings;

public abstract class AbstractConversationSettingsCache {
    protected final String CACHE_KEY_PREFIX = "common:conversation-settings:";
    
    // Sentinel used to prevent cache penetration attacks (caching non-existent database entries)
    protected final ConversationSettings EMPTY_SENTINEL = new ConversationSettings();

    @Value("${common.conversation-settings.cache.ttl:30m}")
    protected Duration cacheTtl;   
    
    protected String getCacheKey(String conversationId) {		
        return CACHE_KEY_PREFIX + conversationId;
    }
}