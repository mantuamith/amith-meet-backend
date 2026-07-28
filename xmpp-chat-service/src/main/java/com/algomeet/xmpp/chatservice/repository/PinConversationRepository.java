package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.PinConversation;
import com.algomeet.xmpp.chatservice.document.PinConversationId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PinConversationRepository extends ReactiveMongoRepository<PinConversation, PinConversationId> {

	/**
     * Finds pinned conversations matching , ordered by seq ascending.
     * Matches: _id.pinnedBy
     * Sorts: { 'seq': 1 } (1 = Ascending, -1 = Descending)
     */
    @Query(value = "{ '_id.pinnedBy': ?0}", 
           sort = "{ 'seq': 1 }")
    Flux<PinConversation> findPinnedConversation(
            UUID pinnedBy
    );
    
    /**
     * Deletes pinned conversation
     */
    Mono<Long> deleteById_ConversationIdAndId_PinnedBy(
            UUID conversationId, 
            UUID pinnedBy
    );
}