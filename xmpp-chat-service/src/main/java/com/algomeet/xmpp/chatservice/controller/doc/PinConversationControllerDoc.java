package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.PinConversationRequest;
import com.algomeet.xmpp.chatservice.dto.PinConversationResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Tag(name = "Pin Conversation", description = "Endpoints for managing pinned 1-on-1 and group chat conversations")
@RequestMapping("/api/chat/conversations")
public interface PinConversationControllerDoc {

	/**
	 * Create a new pinned conversation entry.
	 */
	@Operation(
		summary = "Pin a conversation", 
		description = "Pins a direct 1-to-1 chat (via peerKey) or group chat (via groupId) for the authenticated user. Option to set auto-expiration hours."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "201", 
			description = "Conversation pinned successfully",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "400", 
			description = "Bad Request - Missing required fields (e.g., neither peerKey nor groupId provided)",
			content = @Content(mediaType = "application/json")
		),
		@ApiResponse(
			responseCode = "401", 
			description = "Unauthorized - Missing or invalid security context token",
			content = @Content(mediaType = "application/json")
		)
	})
	public Mono<ResponseEntity<CommonResponse<PinConversationResponse>>> pinConversation(
			@Valid @RequestBody PinConversationRequest request);   

	/**
	 * Remove a pin mapping constraint from a chat window.
	 */
	@Operation(
		summary = "Unpin a conversation", 
		description = "Removes a pinned conversation mapping for the authenticated user by specifying either a peerKey or a groupId."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Conversation unpinned successfully",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "400", 
			description = "Bad Request - Missing required query parameters",
			content = @Content(mediaType = "application/json")
		),
		@ApiResponse(
			responseCode = "404", 
			description = "Not Found - Conversation pin mapping did not exist",
			content = @Content(mediaType = "application/json")
		)
	})
	public Mono<ResponseEntity<CommonResponse<Void>>> unpinConversation(
			@Parameter(description = "The UUID of the direct 1-to-1 chat peer (Required if groupId is null)", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
			@RequestParam(required = false) UUID peerKey, 
			
			@Parameter(description = "The UUID of the group chat (Required if peerKey is null)", example = "f47ac10b-58cc-4372-a567-0e02b2c3d4e5")
			@RequestParam(required = false) UUID groupId,
			
			@Parameter(description = "Active XMPP session ID", required = true, example = "sm-sess-88231")
			@RequestParam(value = "sessionId") String sessionId);

	/**
	 * Get user pinned conversations
	 */
	@Deprecated
	@Operation(
		summary = "Get user pinned conversations", 
		description = "Retrieves all currently active pinned conversations associated with the authenticated user."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Successfully retrieved list of pinned conversations",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "401", 
			description = "Unauthorized - Missing or invalid security token",
			content = @Content(mediaType = "application/json")
		)
	})
	public Mono<ResponseEntity<CommonResponse<List<PinConversationResponse>>>> findPinnedConversations();
}