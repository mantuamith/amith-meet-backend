package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.document.UnreadCount;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "Unread Counts", description = "APIs for managing 1-to-1 chat unread message counts")
@SecurityRequirement(name = "bearerAuth")
public interface UnreadCountControllerDoc {

    @Operation(
        summary = "Get inbox unread counts",
        description = "Retrieve the full list of unread message counts per sender for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved unread counts"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<List<UnreadCount>>>> getInboxUnread();

    @Operation(
        summary = "Get total unread count",
        description = "Retrieve total unread message count (badge count) for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved total unread count"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<Integer>>> getTotalUnread();

    @Operation(
        summary = "Get unread count per sender",
        description = "Retrieve unread message count for a specific chat sender"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved unread count"),
        @ApiResponse(responseCode = "404", description = "Sender not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<Integer>>> getSpecificUnread(
            @Parameter(description = "Unique sender identifier", example = "user-123")
            @PathVariable String senderKey);

    @Deprecated
    @Operation(
            summary = "Advance conversation timeline cutoff checkpoint",
            description = "Advances the temporal visibility boundary for a specific conversation. " +
                          "This action simultaneously resets the unread message counter to zero and " +
                          "synchronizes the timeline state across all alternative active user devices. " +
                          "Can be triggered by opening a conversation or clearing it without reading.",
            responses = {
                @ApiResponse(
                    responseCode = "200", 
                    description = "Timeline checkpoint advanced and unread count successfully synchronized."
                ),
                @ApiResponse(
                    responseCode = "400", 
                    description = "Invalid UUID format supplied for senderKey or cutoffMessageId."
                ),
                @ApiResponse(
                    responseCode = "401", 
                    description = "Unauthorized - Missing or invalid security context token."
                )
            }
        )
    public Mono<ResponseEntity<CommonResponse<?>>> timelineCutoff(
    		@Parameter(
    				name = "senderKey",
    				description = "The unique user key (UUID) of the chat peer/sender",
    				required = true,
    				example = "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    				)
    		@PathVariable String senderKey,

    		@Parameter(
    				name = "cutoffMessageId",
    				description = "The message ID acting as the moving threshold. All messages up to this point are treated as cleared or read.",
    				required = true,
    				example = "289c5f4d-58a0-4def-bf5b-0fd15c045575"
    				)
    		@RequestParam("cutoffMessageId") UUID cutoffMessageId);
}