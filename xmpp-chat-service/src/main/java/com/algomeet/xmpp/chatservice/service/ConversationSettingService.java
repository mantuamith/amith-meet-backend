package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.ConversationSetting;
import com.algomeet.xmpp.chatservice.repository.ConversationSettingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;




@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSettingService {

    private final ConversationSettingRepository repository;
    
    // Inject ReactiveMongoTemplate into your service class
    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Helper to generate the composite ID string.
     */
    private String generateId(String senderUserKey, String receiverUserKey) {
        return senderUserKey + "_" + receiverUserKey;
    }

    /**
     * Retrieve conversation settings for a specific sender and receiver pair.
     */
    public Mono<ConversationSetting> getSettings(String senderUserKey, String receiverUserKey) {
        String id = generateId(senderUserKey, receiverUserKey);
        return repository.findById(id);
    }

    /**
     * Save or fully update conversation settings.
     */
    public Mono<ConversationSetting> saveOrUpdateSettings(ConversationSetting setting) {
        log.info("Saving conversation settings for ID: {}", setting.getId());
        return repository.save(setting);
    }

    /**
     * Convenient upside/update method using explicit sender and receiver keys.
     */
    public Mono<ConversationSetting> saveOrUpdateSettings(String senderUserKey, String receiverUserKey, Long expiration) {
        String id = generateId(senderUserKey, receiverUserKey);
        ConversationSetting setting = new ConversationSetting(id, expiration);
        return repository.save(setting);
    }
   
    /**
     * Delete settings for a specific conversation.
     */
    public void deleteSettings(String senderUserKey, String receiverUserKey) {
        String id = generateId(senderUserKey, receiverUserKey);
        repository.deleteById(id);
        log.info("Deleted conversation settings for ID: {}", id);
    }  
}