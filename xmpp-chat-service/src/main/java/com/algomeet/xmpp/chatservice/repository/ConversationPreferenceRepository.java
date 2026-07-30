package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.ConversationPreference;
import com.algomeet.xmpp.chatservice.document.ConversationPreferenceId;

import reactor.core.publisher.Flux;

@Repository
public interface ConversationPreferenceRepository extends ReactiveMongoRepository<ConversationPreference, ConversationPreferenceId> {
    Flux<ConversationPreference> findById_UserKeyOrderByPinnedSeqAsc(
            UUID userKey
    );     
}