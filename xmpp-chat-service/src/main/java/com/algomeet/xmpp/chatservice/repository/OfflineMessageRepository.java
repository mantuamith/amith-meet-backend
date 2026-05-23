package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OfflineMessageRepository extends ReactiveMongoRepository<OfflineMessage, UUID> {
    
    // Use Flux for a stream of reactive results
    Flux<OfflineMessage> findByToAndDeletedAtIsNullOrderByIdAsc(UUID to);
    
    // Use Mono<Void> for reactive deletion
    Mono<Void> deleteByTo(UUID to);
    
    Mono<OfflineMessage> findByIdAndFromAndDeletedAtIsNull(UUID id, UUID from);
    
    /**
     * Deletes all delivered and read messages
     */
    Mono<Void> deleteByToAndFromAndIdLessThanEqualAndDeletedAtIsNotNull(UUID to, UUID from, UUID id);
    
    // Counts unread messages
    Mono<Long> countByToAndFromAndStanzaIdGreaterThanAndCountableTrue(UUID to, UUID from, UUID stanzaId);
    
    /**
     * Hard-deletes an offline message record matching the given ID 
     * only if its acknowledgement status (isAck) is set to true.
     *
     * @param id The unique message UUID checkpoint
     * @return A Mono<Void> signaling completion when the operation finishes
     */
    Mono<Void> deleteByIdAndIsAckStanzaTrue(UUID id);
}