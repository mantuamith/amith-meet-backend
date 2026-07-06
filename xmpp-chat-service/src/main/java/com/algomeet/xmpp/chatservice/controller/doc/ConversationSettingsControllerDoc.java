package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.ConversationSettingsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Conversation Settings", description = "Endpoints for managing conversation settings and message retention policies")
public interface ConversationSettingsControllerDoc {
    /**
     * Search pinned items dynamically filtered matching safety boundary logic (Self vs Global pinned).
     * Order: Sorted automatically by Sequence ascending.
     * Elements are aggregated via collectList() to safely match the CommonResponse generic envelope structure.
     */
    @GetMapping("/{peerKey}/settings")
    @Operation(
        summary = "Get conversation settings",
        description = "Retrieves the dynamic conversation settings for a given peer key, utilizing cached layers.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved settings"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Settings not found")
        }
    )
    public Mono<ResponseEntity<CommonResponse<ConversationSettingsResponse>>> getConversationSetting(
            @Parameter(description = "The unique UUID of the peer conversation", required = true) 
            @PathVariable UUID peerKey);
    
    /**
     * Search pinned items dynamically filtered matching safety boundary logic (Self vs Global pinned).
     * Order: Sorted automatically by Sequence ascending.
     * Elements are aggregated via collectList() to safely match the CommonResponse generic envelope structure.
     */
    @Operation(
        summary = "Get message retention period",
        description = "Retrieves the specific message retention configuration (in days) for a given peer conversation.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved message retention days"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
        }
    )
    public Mono<ResponseEntity<CommonResponse<Integer>>> getMessageRetention(
            @Parameter(description = "The unique UUID of the peer conversation", required = true) 
            @PathVariable UUID peerKey);
    

    @Operation(
        summary = "Update message retention period",
        description = "Updates the message retention window for the conversation. Handles concurrency conflicts if an update is already processing.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Retention policy updated successfully"),
            @ApiResponse(
                responseCode = "449", // Using 449 or 409 depending on how your client interprets MESSAGE_RETENTION_UPDATE_IN_PROGRESS
                description = "Conflict: An update for this retention policy is already in progress",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
        }
    )
    public Mono<ResponseEntity<CommonResponse<Object>>> updateMessageRetention(
    		@Parameter(description = "The unique UUID of the peer conversation", required = true) 
    		@PathVariable UUID peerKey,
    		@Parameter(description = "The new retention duration in days", required = true) 
    		@RequestParam Integer messageRetentionDays,
    		@Parameter(description = "The active session identifier triggering this change", required = true) 
    		@RequestParam String sessionId);   
}
