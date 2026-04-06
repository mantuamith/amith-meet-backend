package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.xmpp.chatservice.document.CallSession;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CallTrackerRepository extends ReactiveMongoRepository<CallSession, String> {
    // Returns a Mono for non-blocking lookup
    Mono<CallSession> findBySid(String sid);
    
    /**
     * Find any active or past session where the given WebSocket ID was either 
     * the caller OR the callee. Useful for connection cleanup logic.
     */
    Flux<CallSession> findByCallerSidOrCalleSid(String callerSid, String calleSid);
    
    /**
     * Removes a single document matching the SID.
     * Returns Mono<Void> to signal when the deletion is complete.
     */
    Mono<Void> deleteBySid(String sid);

    /**
     * Optional: If you need to know if a document was actually deleted.
     * Returns the number of deleted documents (usually 1 or 0).
     */
    Mono<Long> removeBySid(String sid);
}