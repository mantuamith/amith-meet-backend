package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "MUC Messages", description = "APIs for retrieving group chat messages and message updates")
public interface MucMessageControllerDoc {
	@Operation(
			summary = "Get group chat messages",
			description = """
					Retrieves paginated MUC (Multi-User Chat) messages for a group.

					Supports cursor-based pagination using:
					- beforeStanzaId → fetch older messages
					- afterStanzaId → fetch newer messages

					Only one cursor parameter should be provided per request.
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Messages retrieved successfully",
					content = @Content(
							mediaType = "application/json",
							array = @ArraySchema(schema = @Schema(implementation = MucMessageResponse.class))
					)
			),
			@ApiResponse(responseCode = "400", description = "Invalid request parameters"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Group not found")
	})
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getMessages(

			@Parameter(
					description = "Unique group identifier",
					required = true,
					example = "550e8400-e29b-41d4-a716-446655440000",
					in = ParameterIn.PATH
			)
			@PathVariable UUID groupId,

			@Parameter(
					description = "Fetch messages before the specified stanza ID (older messages)",
					example = "0196b2d7-4b84-7c10-b1d2-91ef4c1cfa11"
			)
			@RequestParam("beforeStanzaId") Optional<String> beforeStanzaId,

			@Parameter(
					description = "Fetch messages after the specified stanza ID (newer messages)",
					example = "0196b2d7-4b84-7c10-b1d2-91ef4c1cfa11"
			)
			@RequestParam("afterStanzaId") Optional<String> afterStanzaId,

			@Parameter(
					description = "Page index",
					example = "0"
			)
			@RequestParam(value = "page", defaultValue = "0") int page,

			@Parameter(
					description = "Page size",
					example = "20"
			)
			@RequestParam(value = "size", defaultValue = "20") int size);

	@Operation(
			summary = "Get message updates",
			description = """
					Retrieves message updates up to the provided stanza ID.

					This endpoint is commonly used for:
					- message synchronization
					- incremental chat updates
					- restoring missed messages
					"""
	)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Message updates retrieved successfully",
					content = @Content(
							mediaType = "application/json",
							array = @ArraySchema(schema = @Schema(implementation = MucMessageResponse.class))
					)
			),
			@ApiResponse(responseCode = "400", description = "Invalid request parameters"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Group not found")
	})
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getMessageUpdates(

			@Parameter(
					description = "Unique group identifier",
					required = true,
					example = "550e8400-e29b-41d4-a716-446655440000",
					in = ParameterIn.PATH
			)
			@PathVariable UUID groupId,

			@Parameter(
					description = "Retrieve updates up to and including this stanza ID",
					required = true,
					example = "0196b2d7-4b84-7c10-b1d2-91ef4c1cfa11"
			)
			@RequestParam("untilStanzaId") UUID untilStanzaId,

			@Parameter(
					description = "Page index",
					example = "0"
			)
			@RequestParam(value = "page", defaultValue = "0") int page,

			@Parameter(
					description = "Page size",
					example = "50"
			)
			@RequestParam(value = "size", defaultValue = "50") int size);
	
	@Operation(
	        summary = "Get user group conversations",
	        description = """
	                Retrieves the chat inbox overview for the currently authenticated user.

	                This endpoint returns all joined MUC (Multi-User Chat) conversations
	                together with the latest visible message from each room.

	                The response automatically respects:
	                - hidden or deleted messages
	                - private whisper visibility rules
	                - message access restrictions

	                Commonly used for:
	                - chat inbox rendering
	                - conversation sidebar initialization
	                - latest message previews
	                - conversation activity ordering
	                """
	)
	@ApiResponses(value = {
	        @ApiResponse(
	                responseCode = "200",
	                description = "Conversations retrieved successfully",
	                content = @Content(
	                        mediaType = "application/json",
	                        array = @ArraySchema(
	                                schema = @Schema(implementation = MucMessageResponse.class)
	                        )
	                )
	        ),
	        @ApiResponse(responseCode = "401", description = "Unauthorized"),
	        @ApiResponse(responseCode = "403", description = "Forbidden")
	})
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getConversations();
}
