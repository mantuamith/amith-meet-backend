package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.PinMucMessage;
import com.algomeet.xmpp.chatservice.document.PinMucMessageId;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PinMucMessageRepository extends ReactiveMongoRepository<PinMucMessage, PinMucMessageId> {

	/**
     * Finds pinned messages matching your exact compound index structure, ordered by seq ascending.
     * Matches: conversationId AND (pinnedBy OR pinnedForEveryone == true)
     * Sorts: { 'seq': 1 } (1 = Ascending, -1 = Descending)
     */
    @Query(value = "{ '_id.groupId': ?0, '$or': [ { 'pinnedBy': ?1 }, { 'pinnedForEveryone': true } ] }", 
           sort = "{ 'seq': 1 }")
    Flux<PinMucMessage> findPinnedMessages(
            String conversationId, 
            UUID pinnedBy
    );

    /**
     * Retrieves all active pinned messages for a specific conversation.
     */
    Flux<PinMucMessage> findById_GroupId(String conversationId);

     /**
     * Deletes a global pin for everyone within a conversation.
     */
    Mono<Long> deleteById_GroupIdAndId_MessageIdAndPinnedForEveryoneIsTrue(
            UUID groupId, 
            UUID messageId
    );
    
    /**
     * Deletes a record by its nested composite ID properties and returns the deletion count (0 or 1).
     * Spring Data automatically handles fields prefixed with 'id' or '_id' for composite keys.
     */
    Mono<Long> deleteById_GroupIdAndId_MessageIdAndId_PinnedBy(
            UUID groupId, 
            UUID messageId, 
            UUID pinnedBy
    );
}