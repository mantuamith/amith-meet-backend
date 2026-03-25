package com.algomeet.xmpp.chatservice.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;

public interface OfflineMessageRepository extends ReactiveMongoRepository<OfflineMessage, String> {
    
    // Find messages to deliver when recipient sends non-negative presence
    List<OfflineMessage> findByToOrderByCreatedAtAsc(String to);
    
    // Delete after delivery
    void deleteByTo(String to);
}