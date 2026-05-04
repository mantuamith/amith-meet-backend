package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OfflineMessageRepository extends ReactiveMongoRepository<OfflineMessage, String> {
    
    // Use Flux for a stream of reactive results
    Flux<OfflineMessage> findByToOrderByCreatedAtAsc(String to);
    
    // Use Mono<Void> for reactive deletion
    Mono<Void> deleteByTo(String to);
    
    Mono<OfflineMessage> findByIdAndFrom(String id, String from);
}