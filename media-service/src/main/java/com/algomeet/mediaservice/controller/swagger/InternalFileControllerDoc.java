package com.algomeet.mediaservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.mediaservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Internal Media API", description = "Service-to-service media operations. Not exposed to the public gateway.")
public interface InternalFileControllerDoc {

    // ========================= SHARE =========================

    @Operation(
        summary = "Share a media file (internal)",
        description = "Called by the chat-service to grant file access to message recipients after delivery.",
        responses = {
            @ApiResponse(responseCode = "200", description = "File shared successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    ResponseEntity<?> share(
            @Parameter(description = "Media ID", required = true)
            @PathVariable UUID mediaId,

            @Parameter(description = "User key of the caller (owner / sharer)", required = true)
            @RequestParam String userKey,

            @Parameter(description = "User keys to grant access to", required = true)
            @RequestParam List<String> shareWithUserKeys,
            
            @Parameter(description = "The chat message ID where this file was originally attached", required = true)
            @RequestParam UUID messageId
    );

    // ========================= DELETE =========================

    @Operation(
        summary = "Delete a media file (internal)",
        description = "Soft-deletes a media file on behalf of a user. Called by the chat-service when a message is retracted.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Delete successful"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Media ID", required = true)
            @PathVariable UUID mediaId,

            @Parameter(description = "User key of the caller", required = true)
            @RequestParam String userKey,

            @Parameter(description = "Additional user keys whose access should also be revoked")
            @RequestParam(required = false)
            List<String> deleteWithUserKeys,
            
            @Parameter(description = "The chat message ID where this file was originally attached", required = true)
            @RequestParam UUID messageId
    );
}
