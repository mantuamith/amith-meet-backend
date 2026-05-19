package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OfflineMessageRepository extends ReactiveMongoRepository<OfflineMessage, UUID> {
    
    // Use Flux for a stream of reactive results
    Flux<OfflineMessage> findByToAndDeletedAtIsNullOrderByIdAsc(String to);
    
    // Use Mono<Void> for reactive deletion
    Mono<Void> deleteByTo(String to);
    
    Mono<OfflineMessage> findByIdAndFromAndDeletedAtIsNull(UUID id, String from);
    
    /**
     * Deletes all pending offline messages for a specific recipient 
     * up to and including the specified message ID checkpoint.
     *
     * @param to        The receiver user key/ID whose offline queue is being cleared
     * @param id The highest stanza ID/UUIDv7 that was successfully delivered (inclusive)
     * @return A Mono signaling completion when the database purge finishes
     */
    Mono<Void> deleteByToAndIdLessThan(String to, UUID id);
    
    // Counts pending messages for a user that are newer than a specific message ID checkpoint
    Mono<Long> countByToAndIdGreaterThanAndDeletedAtIsNull(String to, UUID id);
}