package com.algomeet.signalservice.repository;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import com.algomeet.signalservice.document.ConversationSetting;

@Repository
public interface ConversationSettingRepository extends MongoRepository<ConversationSetting, String> {
    /**
     * Updates or sets the message retention days for a specific conversation ID.
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'messageRetentionDays': ?1 } }")
    Long updateMessageRetentionDays(String conversationId, Integer messageRetentionDays);
}