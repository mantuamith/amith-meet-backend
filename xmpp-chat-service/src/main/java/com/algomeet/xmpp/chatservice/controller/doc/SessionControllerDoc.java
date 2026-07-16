package com.algomeet.xmpp.chatservice.controller.doc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

/**
 * REST controller for managing active XMPP/WebSocket sessions and Redis-based presence states.
 */
@Tag(name = "Session Management", description = "APIs for managing active sessions and distributed presence states")
@SecurityRequirement(name = "bearerAuth")
public interface SessionControllerDoc {

    @Operation(
        summary = "Remove session presence and state",
        description = """
            Removes the user's presence state (Active, Inactive, etc.) and terminates the session mapping in Redis.
            
            This operation is primarily used to:
            - Evict 'zombie' or orphan records from Redis that no longer have an active connection.
            - Synchronize the distributed state store by removing stale user presence data.
            - Forcefully close the associated Netty channel if it remains open on the local node.
            
            Typically used for:
            - Cleanup of orphaned records during node rebalancing or crashes.
            - Manual presence reset for specific devices.
            - Security enforcement and session eviction.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session state and presence records successfully removed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Valid JWT required"),
        @ApiResponse(responseCode = "403", description = "Forbidden - The session does not belong to the authenticated user"),
        @ApiResponse(responseCode = "404", description = "Session or state record not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error during Redis operation")
    })
    public Mono<ResponseEntity<CommonResponse<?>>> removeSession(
            @Parameter(
                description = "Unique identifier for the session or presence record to be evicted",
                example = "a3f9c2d1-9b7e-4c6f-8a12-abc123xyz"
            )
            @PathVariable String sessionId);
}