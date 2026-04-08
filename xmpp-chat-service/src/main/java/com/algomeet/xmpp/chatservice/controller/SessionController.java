package com.algomeet.xmpp.chatservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.SessionService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for managing active XMPP and WebSocket sessions.
 * <p>
 * This controller provides endpoints for session maintenance, allowing for the 
 * manual eviction of specific client connections (e.g., during logout, security 
 * breaches, or account suspension).
 * </p>
 * <p>
 * <b>Security:</b> All operations are scoped to the authenticated user's context 
 * retrieved via {@link SecurityUtil}.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * Manually removes and terminates a specific session for the authenticated user.
     * <p>
     * This endpoint triggers the following sequence:
     * <ol>
     * <li>Retrieves the unique {@code userKey} from the current security context.</li>
     * <li>Invokes {@link SessionService#removeSession} to clear session metadata from the cluster.</li>
     * <li>Forcefully closes the associated Netty channel if it is hosted on the local node.</li>
     * </ol>
     * </p>
     *
     * @param sessionId The unique identifier (Resource/UUID) of the session to be evicted.
     * @return A {@link ResponseEntity} containing a {@link CommonResponse} with the operation status.
     * @throws Exception if the session removal logic encounters a persistence or communication error.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<CommonResponse<?>> removeSession(
            @PathVariable String sessionId) {
        
        // Retrieve the identifier for the currently authenticated user
        String userKey = SecurityUtil.getUserKey();
        
        log.info("Request to manually evict session {} for user {}", sessionId, userKey);
        
        try {
            // Execute the removal logic through the service layer
            sessionService.removeSession(userKey, sessionId);
            
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (Exception e) {
            log.error("Failed to remove session {} for user {}", sessionId, userKey, e);
            // Re-throw to be handled by the GlobalExceptionHandler/ControllerAdvice
            throw e;
        }
    }
}