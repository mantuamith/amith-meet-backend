package com.algomeet.xmpp.chatservice.controller.doc;


import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.ArchiveConversationRequest;
import com.algomeet.xmpp.chatservice.dto.ArchiveConversationResponse;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@Tag(name = "Archive Conversation", description = "Endpoints for archiving and unarchiving chat conversations")
public interface ArchiveConversationControllerDoc {

	/**
	 * Create a new archive message entry.
	 */
	@Operation(
		summary = "Archive a conversation", 
		description = "Archives a direct message (via peerKey) or a group chat (via groupId). Either peerKey or groupId must be provided."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Conversation archived successfully",
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
	public Mono<ResponseEntity<CommonResponse<ArchiveConversationResponse>>> archiveConversation(
			@Valid @RequestBody ArchiveConversationRequest request);
	
	/**
	 * Remove an archive mapping constraint from a chat window.
	 */
	@Operation(
		summary = "Unarchive a conversation", 
		description = "Removes the archived status from a conversation given a peerKey or groupId along with the active sessionId."
	)
	@ApiResponses(value = {
		@ApiResponse(
			responseCode = "200", 
			description = "Conversation unarchived successfully",
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
