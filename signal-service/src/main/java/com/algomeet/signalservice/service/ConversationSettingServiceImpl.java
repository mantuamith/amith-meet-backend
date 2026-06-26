package com.algomeet.signalservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.signalservice.document.ConversationSetting;
import com.algomeet.signalservice.repository.ConversationSettingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSettingServiceImpl implements ConversationSettingsService {
    private final ConversationSettingRepository repository;

    /**
     * Generates the deterministic conversation ID using lexicographical ordering.
     * Format: lowerUserKey_higherUserKey
     */
    @Override
    public String getConversationId(UUID userKeyA, UUID userKeyB) {
        String strA = userKeyA.toString();
        String strB = userKeyB.toString();
        return strA.compareTo(strB) < 0 ? strA + "_" + strB : strB + "_" + strA;
    }

    /**
     * Retrieves the conversation settings for a direct chat between two users.
     * If no settings exist, it returns a default configuration object (never expire).
     */
    public ConversationSetting getSettings(UUID userKeyA, UUID userKeyB) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        
        return repository.findById(conversationId)
                .orElseGet(() -> new ConversationSetting(conversationId, -1)); // Default: never expire
    }

    /**
     * Upserts (saves or updates) the retention policy for a direct chat conversation.
     */
    public ConversationSetting saveOrUpdateRetentionDays(UUID userKeyA, UUID userKeyB, Integer retentionDays) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        ConversationSetting setting = new ConversationSetting(conversationId, retentionDays);
        
        log.info("Saving conversation setting for ID: {} with retention days: {}", conversationId, retentionDays);
        return repository.save(setting);
    }

    /**
     * Optimized partial update targeting only the retention days field.
     */
    public Boolean updateRetentionDaysOnly(UUID userKeyA, UUID userKeyB, Integer retentionDays) {
        String conversationId = getConversationId(userKeyA, userKeyB);
        
        long modifiedCount = repository.updateMessageRetentionDays(conversationId, retentionDays);
        return modifiedCount > 0;
    }

    /**
     * Interface implementation returning the generic domain DTO layer.
     */
    @Override
    public ConversationSettings getSettings(String conversationId) {
        ConversationSetting entity = repository.findById(conversationId)
                .orElseGet(() -> new ConversationSetting(conversationId, -1));

        ConversationSettings dto = new ConversationSettings();
        dto.setMessageRetentionDays(entity.getMessageRetentionDays());
        return dto;
    }
}
