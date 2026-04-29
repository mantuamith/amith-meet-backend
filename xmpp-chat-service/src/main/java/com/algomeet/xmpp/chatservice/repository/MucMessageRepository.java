package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.algomeet.xmpp.chatservice.document.MucMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MucMessageRepository extends ReactiveMongoRepository<MucMessage, String> {

    /**
     * Retrieve a page of messages for a room starting AFTER a specific sequential ID.
     * Ideal for infinite scroll / MAM 'after' queries.
     * 
     * Version with a limit to satisfy MAM 'max' requests (XEP-0059)
     */
    Flux<MucMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(
        String roomId, String afterId, Pageable pageable
    );
    
    /**
     * Retrieves older messages (scrolling up).
     * Maps to MAM 'before' logic: get 'max' messages where ID < beforeId.
     * If beforeId is null/empty, you get the most recent messages.
     * If beforeId is provided, you get the page preceding that ID.
     */
    Flux<MucMessage> findByRoomIdAndIdLessThanOrderByIdDesc(
    		String roomId, String beforeId, Pageable pageable
    		);
        
    // For the very first load (no cursor)
    Flux<MucMessage> findByRoomIdOrderByIdDesc(String roomId, Pageable pageable);
    
    /**
     * Efficiently counts unread messages using the {roomId: 1, id: 1} compound index.
     */
    Mono<Long> countByRoomIdAndIdGreaterThanAndFromNot(String roomId, String lastReadId, String userJid);
    
    
    /**
     * Fetches messages for a specific room that occurred after the given ULID/ID.
     * Sorted Ascending so the client receives them in chronological order.
     */
    Flux<MucMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(String roomId, String afterId);
    
    Mono<MucMessage> findByMessageId(String messageId);
}