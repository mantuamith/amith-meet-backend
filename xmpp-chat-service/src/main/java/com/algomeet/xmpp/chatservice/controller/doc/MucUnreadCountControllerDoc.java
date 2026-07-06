package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MucUnreadCount;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "MUC Unread Counts", description = "APIs for managing unread message counts in multi-user chat rooms")
public interface MucUnreadCountControllerDoc {

    @Operation(
        summary = "Get unread rooms",
        description = "Retrieve all chat rooms with unread message counts for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved unread rooms"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<List<MucUnreadCount>>>> getUnreadRooms();

    @Operation(
        summary = "Get total unread count",
        description = "Retrieve the total unread message count across all MUC rooms for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved total unread count"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<Integer>>> getTotalUnread();

    @Operation(
        summary = "Get unread count per room",
        description = "Retrieve unread message count for a specific chat room"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved room unread count"),
        @ApiResponse(responseCode = "404", description = "Room not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public Mono<ResponseEntity<CommonResponse<Integer>>> getRoomUnread(
            @Parameter(description = "ID of the chat room", example = "1001")
            @PathVariable String roomId);
}