package com.algomeet.xmpp.chatservice.repository;

import com.algomeet.xmpp.chatservice.document.MucMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MucMessageRepository extends ReactiveMongoRepository<MucMessage, String> {

    /**
     * Retrieve a page of messages for a room starting AFTER a specific sequential ID.
     * Ideal for infinite scroll / MAM 'after' queries.
     */
    Flux<MucMessage> findByRoomIdAndIdGreaterThanOrderByIdAsc(
        String roomId, String lastSeenId, Pageable pageable
    );
    
    
    /**
     * Efficiently counts unread messages using the {roomId: 1, id: 1} compound index.
     */
    Mono<Long> countByRoomIdAndIdGreaterThanAndFromNot(String roomId, String lastReadId, String userJid);
}