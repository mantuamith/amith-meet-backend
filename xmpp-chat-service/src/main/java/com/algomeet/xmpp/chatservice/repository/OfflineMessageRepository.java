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
     * Deletes all delivered and read messages
     */
    Mono<Void> deleteByToAndFromAndIdLessThanEqualAndDeletedAtIsNotNull(String to, String from, UUID id);
    
    // Counts unread messages
    Mono<Long> countByToAndFromAndIdGreaterThan(String to, String from, UUID id);
}