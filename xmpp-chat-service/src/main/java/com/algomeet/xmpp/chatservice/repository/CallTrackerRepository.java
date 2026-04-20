package com.algomeet.xmpp.chatservice.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import com.algomeet.xmpp.chatservice.document.CallSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface CallTrackerRepository extends ReactiveMongoRepository<CallSession, String> {
    
    /**
     * Retrieves all sessions for a specific SID where the roomId is null.
     * This is typically used for 1-on-1 calls, excluding group/MUC calls.
     */
    Flux<CallSession> findAllBySidAndRoomIdIsNull(String sid);
    
	/**
	 * Retrieves all sessions for a specific SID where a roomId exists.
	 * Useful for identifying calls associated with a group/MUC room.
	 */
	Flux<CallSession> findAllBySidAndRoomIdIsNotNull(String sid);
    
    /**
     * Forces a single result by returning the most recently created session
     * for a specific SID and Callee JID.
     */
    Mono<CallSession> findFirstBySidAndCalleeOrderByCreatedAtDesc(String sid, String callee);
    
    /**
     * Find any active or past session where the given WebSocket ID was either 
     * the caller OR the callee. Useful for connection cleanup logic.
     */
    Flux<CallSession> findByCallerSidOrCalleeSid(String callerSid, String calleeSid);
    
    /**
     * Removes documents matching the SID and Callee.
     */
    Mono<Void> deleteBySidAndCallee(String sid, String calleeSid);
    
    /**
     * Removes documents matching the SID.
     */
    Mono<Void> deleteBySid(String sid);

    /**
     * Returns the count of deleted documents.
     */
    Mono<Long> removeBySid(String sid);
}