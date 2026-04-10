package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

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

    @Operation(
        summary = "Reset unread count per sender",
        description = "Reset unread message count to zero when user opens a conversation"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully reset unread count"),
        @ApiResponse(responseCode = "404", description = "Sender not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<?>>> resetCount(
            @Parameter(description = "Unique sender identifier", example = "user-123")
            @PathVariable String senderKey);
}