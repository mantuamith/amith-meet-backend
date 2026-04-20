package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.SmBufferMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for XEP-0198 Stream Management buffering.
 *
 * Responsibilities:
 * - Persist unacknowledged stanzas per SM session
 * - Support deterministic replay during session resume
 * - Cleanup acknowledged stanzas after client ACK
 *
 * Ordering strategy:
 * - Primary ordering is based on monotonic SEQ (not timestamp)
 * - Ensures strict replay consistency per XMPP spec
 */
@Repository
public interface SmBufferMessageRepository
        extends ReactiveMongoRepository<SmBufferMessage, String> {

    /**
     * Retrieves buffered stanzas for a given SM session
     * ordered by monotonic sequence number.
     *
     * This ensures correct replay order after resume.
     */
    Flux<SmBufferMessage> findBySmSidOrderBySeqAsc(String smSid);

    /**
     * Deletes all buffered stanzas for a given SM session.
     * Typically used after successful ACK reconciliation or session teardown.
     */
    Mono<Void> deleteBySmSid(String smSid);

    /**
     * Deletes a specific stanza by its unique ID.
     * Used for granular ACK-based cleanup if required.
     */
    Mono<Void> deleteById(String id);
}