package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.ConversationSettingsDocument;

import reactor.core.publisher.Mono;

@Repository
public interface ConversationSettingRepository extends ReactiveMongoRepository<ConversationSettingsDocument, String> {
    /**
     * Updates or sets the message retention days for a specific conversation ID.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'messageRetentionDays': ?1 } }")
    Mono<Long> updateMessageRetentionDays(String conversationId, Integer messageRetentionDays);
}