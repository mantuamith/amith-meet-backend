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

/**
 * REST controller for managing active XMPP and WebSocket sessions.
 */

@Tag(name = "Session Management", description = "APIs for managing active XMPP/WebSocket sessions")
@SecurityRequirement(name = "bearerAuth")
public interface SessionControllerDoc {
    @Operation(
        summary = "Terminate a session",
        description = """
            Manually removes and terminates a specific session for the authenticated user.

            This operation will:
            - Validate ownership of the session
            - Remove session metadata from the distributed store
            - Forcefully close the associated Netty channel (if local)

            Typically used for:
            - Logout from specific device
            - Security enforcement
            - Session cleanup
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Session successfully terminated"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden (session does not belong to user)"),
        @ApiResponse(responseCode = "404", description = "Session not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponse<?>> removeSession(
            @Parameter(
                description = "Unique session identifier (XMPP resource or UUID)",
                example = "a3f9c2d1-9b7e-4c6f-8a12-abc123xyz"
            )
            @PathVariable String sessionId);
}