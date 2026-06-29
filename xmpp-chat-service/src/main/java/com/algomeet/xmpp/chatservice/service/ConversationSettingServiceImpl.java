package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.xmpp.chatservice.document.ConversationSetting;
import com.algomeet.xmpp.chatservice.repository.ConversationSettingRepository;
import com.algomeet.xmpp.chatservice.util.DeterministicConversationIdUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSettingServiceImpl implements ConversationSettingsService{

    private final ConversationSettingRepository repository;

    /**
     * Generates the deterministic conversation ID using lexicographical ordering.
     * Format: lowerUserKey_higherUserKey
     */
    public String getConversationId(UUID userKeyA, UUID userKeyB) {        
        return DeterministicConversationIdUtil.getConversationId(userKeyA, userKeyB);
    }

    /**
     * Retrieves the conversation settings for a direct chat between two users.
     * If no settings exist, it returns a default configuration object (never expire).
     */
    public Mono<ConversationSetting> getSettings(UUID userKeyA, UUID userKeyB) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        
        return repository.findById(conversationId)
                .defaultIfEmpty(new ConversationSetting(conversationId, -1)); // Default: never expire
    }

    /**
     * Upserts (saves or updates) the retention policy for a direct chat conversation.
     */
    public Mono<ConversationSetting> saveOrUpdateRetentionDays(UUID userKeyA, UUID userKeyB, Integer retentionDays) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        ConversationSetting setting = new ConversationSetting(conversationId, retentionDays);
        
        log.info("Saving conversation setting for ID: {} with retention days: {}", conversationId, retentionDays);
        return repository.save(setting);
    }

    /**
     * Optimized partial update targeting only the retention days field.
     */
    public Mono<Boolean> updateRetentionDaysOnly(UUID userKeyA, UUID userKeyB, Integer retentionDays) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        
        return repository.updateMessageRetentionDays(conversationId, retentionDays)
                .map(modifiedCount -> modifiedCount > 0);
    }

    @Override
    public Mono<ConversationSettings> getSettings(String conversationId) {
        return repository.findById(conversationId)
                // 1. If it doesn't exist in the database, provide a default fallback entity
                .defaultIfEmpty(new ConversationSetting(conversationId, -1))
                // 2. Map the Database Entity to your expected DTO structure
                .map(entity -> {
                    ConversationSettings dto = new ConversationSettings();
                    dto.setMessageRetentionDays(entity.getMessageRetentionDays());
                    return dto;
                });
    }
}