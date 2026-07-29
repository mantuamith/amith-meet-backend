package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.ConversationPreference;
import com.algomeet.xmpp.chatservice.document.ConversationPreferenceId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ConversationPreferenceRepository extends ReactiveMongoRepository<ConversationPreference, ConversationPreferenceId> {

	/**
     * Finds owner conversations matching , ordered by seq ascending.
     * Matches: _id.pinnedBy
     * Sorts: { 'seq': 1 } (1 = Ascending, -1 = Descending)
     */
    @Query(value = "{ '_id.userKey': ?0}", 
           sort = "{ 'seq': 1 }")
    Flux<ConversationPreference> findPinnedConversation(
            UUID pinnedBy
    );    
   
}