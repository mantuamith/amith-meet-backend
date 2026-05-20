package com.algomeet.xmpp.chatservice.repository;

import java.util.UUID;

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
	 * Retrieves the first session for the specified SID where roomId is present.
	 * Useful for resolving a group/MUC call session linked to a room.
	 *
	 * @param sid shared session identifier of the call
	 * @return first matching CallSession with a non-null roomId
	 */
	Mono<CallSession> findFirstBySidAndRoomIdIsNotNull(String sid);
	
    /**
     * Forces a single result by returning the most recently created session
     * for a specific SID and Callee JID.
     */
    Mono<CallSession> findFirstBySidAndCalleeOrderByCreatedAtDesc(String sid, UUID callee);
    

    /**
     * Finds one-to-one call sessions where the given SID is either the caller
     * session ID or callee session ID.
     *
     * @param callerSid caller session identifier to match
     * @param calleeSid callee session identifier to match
     * @return matching direct call sessions
     */
    Flux<CallSession> findByCallerSidOrCalleeSid(
            String callerSid,
            String calleeSid
    );
    
    /**
     * Removes documents matching the SID and Callee.
     */
    Mono<Void> deleteBySidAndCallee(String sid, UUID callee);
    
    /**
     * Removes documents matching the SID.
     */
    Mono<Void> deleteBySid(String sid);

    /**
     * Returns the count of deleted documents.
     */
    Mono<Long> removeBySid(String sid);
}