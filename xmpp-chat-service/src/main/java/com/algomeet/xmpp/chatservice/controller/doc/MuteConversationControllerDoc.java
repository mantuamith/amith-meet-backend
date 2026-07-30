package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MuteConversationRequest;
import com.algomeet.xmpp.chatservice.dto.MuteConversationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Tag(name = "Mute Conversation", description = "Endpoints for muting and unmuting chat conversations")
public interface MuteConversationControllerDoc {

	/**
	 * Create a new mute message entry.
	 */
	@Operation(
		summary = "Mute a conversation", 
		description = "Mutes notifications for a direct conversation (via peerKey) or a group chat (via groupId). "
				+ "Optionally accepts a duration (in hours) via `muteUntil`. Either peerKey or groupId must be provided."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Conversation muted successfully",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "400", 
			description = "Bad Request - Neither peerKey nor groupId was provided",
			content = @Content
		),
		@ApiResponse(
			responseCode = "401", 
			description = "Unauthorized - Invalid or missing authentication context",
			content = @Content
		)
	})
	public Mono<ResponseEntity<CommonResponse<MuteConversationResponse>>> muteConversation(
			@Valid @RequestBody MuteConversationRequest request);  

	/**
	 * Remove a mute mapping constraint from a chat window.
	 */
	@Operation(
		summary = "Unmute a conversation", 
		description = "Removes the muted status from a conversation given a peerKey or groupId along with the active sessionId."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Conversation unmuted successfully",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "400", 
			description = "Bad Request - Neither peerKey nor groupId was provided",
			content = @Content
		),
		@ApiResponse(
			responseCode = "401", 
			description = "Unauthorized - Invalid or missing authentication context",
			content = @Content
		)
	})
	public Mono<ResponseEntity<CommonResponse<Void>>> unpinConversation(
			@Parameter(description = "UUID of the peer user (for 1-on-1 chats)", example = "123e4567-e89b-12d3-a456-426614174000")
	        @RequestParam(required = false) UUID peerKey,             
	        
			@Parameter(description = "UUID of the chat group (for group chats)", example = "987f6543-e21b-12d3-a456-426614174000")
	        @RequestParam(required = false) UUID groupId,
	        
			@Parameter(description = "Active chat session ID", required = true, example = "sess_abc123xyz")
	        @RequestParam(value = "sessionId") String sessionId);
}
