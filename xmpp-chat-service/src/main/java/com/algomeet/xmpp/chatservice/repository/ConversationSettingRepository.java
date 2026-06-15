package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.ConversationSetting;

import reactor.core.publisher.Mono;

@Repository
public interface ConversationSettingRepository extends ReactiveMongoRepository<ConversationSetting, String> {

    // Helper to find by sender and receiver keys by reconstructing the ID format
    default Mono<ConversationSetting> findBySenderAndReceiver(String senderUserKey, String receiverUserKey) {
        String compositeId = senderUserKey + "_" + receiverUserKey;
        return findById(compositeId);
    }
}