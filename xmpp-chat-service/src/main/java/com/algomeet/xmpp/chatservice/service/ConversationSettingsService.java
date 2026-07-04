package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.service.ConversationIdProvider;
import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.document.ConversationSettingsDocument;
import com.algomeet.xmpp.chatservice.repository.ConversationSettingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSettingsService implements ConversationIdProvider {

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
    public Mono<ConversationSettingsDocument> getSettings(UUID userKeyA, UUID userKeyB) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        
        return repository.findById(conversationId)
                .defaultIfEmpty(new ConversationSettingsDocument(conversationId, -1)); // Default: never expire
    }

    /**
     * Upserts (saves or updates) the retention policy for a direct chat conversation.
     * Finds the existing setting first before performing the update.
     */
    public Mono<ConversationSettingsDocument> saveOrUpdateRetentionDays(UUID userKeyA, UUID userKeyB, Integer retentionDays) {
        String conversationId = getConversationId(userKeyA, userKeyB);        
        log.info("Processing conversation setting for ID: {} with retention days: {}", conversationId, retentionDays);
        
        return repository.findById(conversationId)
                .flatMap(existingSetting -> {
                    log.debug("Found existing setting for ID: {}. Updating retention days.", conversationId);
                    existingSetting.setMessageRetentionDays(retentionDays); // Assuming a setter exists
                    return repository.save(existingSetting);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No existing setting found for ID: {}. Creating new record.", conversationId);
                    ConversationSettingsDocument newSetting = new ConversationSettingsDocument(conversationId, retentionDays);
                    return repository.save(newSetting);
                }));
    }

    public Mono<com.algomeet.common.dto.ConversationSettings> getSettings(String conversationId) {
        return repository.findById(conversationId)
                // 1. If it doesn't exist in the database, provide a default fallback entity
                .defaultIfEmpty(new ConversationSettingsDocument(conversationId, -1))
                // 2. Map the Database Entity to your expected DTO structure
                .map(entity -> {
                	com.algomeet.common.dto.ConversationSettings dto = new com.algomeet.common.dto.ConversationSettings();
                    dto.setMessageRetentionDays(entity.getMessageRetentionDays());
                    return dto;
                });
    }   
}