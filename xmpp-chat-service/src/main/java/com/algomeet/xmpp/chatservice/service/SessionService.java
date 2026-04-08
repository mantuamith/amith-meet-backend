package com.algomeet.xmpp.chatservice.service;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service layer responsible for orchestrating user session lifecycles.
 * <p>
 * This service provides a high-level interface for managing active connections, 
 * bridging the gap between external controllers (or internal listeners) and the 
 * {@link UserSessionRegistry}.
 * </p>
 * <p>
 * <b>Cluster Impact:</b> Actions performed here directly influence the global session 
 * state stored in Redis, ensuring consistency across all nodes in the AlgoMeet cluster.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionRegistry userSessionRegistry;
    
    /**
     * Terminates a specific session for a user and cleans up associated metadata.
     * <p>
     * This method triggers the removal of the session from the distributed registry. 
     * If the session is associated with a physical connection on the current node, 
     * the registry implementation typically handles the necessary resource cleanup.
     * </p>
     *
     * @param userKey   The unique identifier of the user (e.g., account ID or email).
     * @param sessionId The specific resource or UUID of the session to be removed.
     */
    public void removeSession(String userKey, String sessionId) {
        // Delegate the actual data removal to the Redis-backed registry
        userSessionRegistry.removeSession(userKey, sessionId);
        
        log.debug("Session {} successfully removed from Redis for user {}", sessionId, userKey);
    }
}