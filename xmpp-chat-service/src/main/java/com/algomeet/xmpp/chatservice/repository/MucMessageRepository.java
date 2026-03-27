package com.algomeet.xmpp.chatservice.repository;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.algomeet.xmpp.chatservice.document.MucMessage;

import reactor.core.publisher.Flux;

public interface MucMessageRepository extends ReactiveMongoRepository<MucMessage, String> {

    /**
     * Standard MAM query: returns a non-blocking stream of messages.
     * We use Flux<MucMessage> to allow the XMPP server to stream results 
     * back to the client as they are read from the database.
     */
    Flux<MucMessage> findByRoomJidAndTimestampBetweenOrderByTimestampAsc(
        String roomJid, Instant start, Instant end, Pageable pageable
    );
}