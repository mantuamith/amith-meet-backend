package com.algomeet.xmpp.chatservice.controller.doc;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.dto.GetMessagesByIdsRequest;
import com.algomeet.xmpp.chatservice.dto.HideMessageRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Mono;

@Tag(name = "MUC Messages", description = "APIs for retrieving group chat messages and message updates")
public interface MucMessageControllerDoc {
	@Operation(
			summary = "Get group chat messages",
			description = """
					Retrieves paginated MUC (Multi-User Chat) messages for a group.

					Supports cursor-based pagination using:
					- before → fetch older messages
					- after→ fetch newer messages

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
	public Mono<ResponseEntity<CommonResponse<List<MucMessageResponse>>>> getMessages(

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
			@RequestParam("before") UUID beforeStanzaId,

			@Parameter(
					description = "Fetch messages after the specified stanza ID (newer messages)",
					example = "0196b2d7-4b84-7c10-b1d2-91ef4c1cfa11"
					)
			@RequestParam("after") UUID afterStanzaId,

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
			summary = "Get modified messages",
			description = """
					Retrieves modified messages up to the provided stanza ID.

					This endpoint is commonly used for:
					- message synchronization
					- incremental chat updates
					- restoring missed messages
					"""
			)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Modified messages retrieved successfully",
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
	public Mono<ResponseEntity<CommonResponse<List<MucMessageResponse>>>> getModifiedMessages(

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
					example = "20"
					)
			@RequestParam(value = "size", defaultValue = "20") int size);

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
	public Mono<ResponseEntity<CommonResponse<List<MucMessageResponse>>>> getConversations();

	@Operation(
			summary = "Hard delete all group messages administratively",
			description = "Permanently and irreversibly purges all recorded message stanzas belonging to this specific Multi-User Chat (MUC) group from persistent storage. " +
					"This action drops records globally for all occupants and immediately invalidates active group cache pools. " +
					"Access is restricted to authorized room owners or system administrators.",
					responses = {
							@ApiResponse(
									responseCode = "200", 
									description = "All group messages were successfully expunged from the database and cache layers.",
									content = @Content(
											mediaType = "application/json",
											schema = @Schema(implementation = CommonResponse.class)
											)
									),
							@ApiResponse(
									responseCode = "400", 
									description = "Invalid request format - Provided groupId is not a valid UUID string.",
									content = @Content
									),
							@ApiResponse(
									responseCode = "403", 
									description = "Forbidden - The requesting security context lacks the administrative privileges to purge this room.",
									content = @Content
									),
							@ApiResponse(
									responseCode = "500", 
									description = "Internal Server Error - Remote microservice transaction failure or database execution timeout.",
									content = @Content
									)
			}
			)
	public Mono<ResponseEntity<CommonResponse<Boolean>>> purgeGroupMessages(
			@Parameter(
					name = "groupId",
					description = "The unique room/group identifier (UUID) targeting the message archive to be wiped.",
					required = true,
					example = "289c5f4d-58a0-4def-bf5b-0fd15c045575"
					)
			@PathVariable UUID groupId);

	@Tag(name = "MUC Room Administration", description = "Endpoints managing Multi-User Chat configurations and retention policies.")
	@Operation(
			summary = "Apply room message retention policy",
			description = "Configures the duration (in days) that messages are retained within a specific MUC group before being eligible for automatic purging. Requires OWNER or ADMIN affiliation."
			)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200", 
					description = "Retention policy applied successfully.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					),
			@ApiResponse(
					responseCode = "403", 
					description = "Unauthorized. The user is not an Admin or Owner of this group.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					),
			@ApiResponse(
					responseCode = "404", 
					description = "Group not found.",
					content = @Content(schema = @Schema(hidden = true))
					)
	})
	public Mono<ResponseEntity<CommonResponse<Object>>> updateMessageRetention(
			@Parameter(description = "The unique identifier (UUID) of the MUC group room", required = true)
			@PathVariable UUID groupId,

			@Parameter(description = "Number of days messages should be retained from creation time", required = true, example = "30")
			@RequestParam Integer messageRetentionDays,

			@Parameter(description = "The unique session ID of the connected user", 
				    required = true)
    		@RequestParam String sessionId);


	@Operation(
			summary = "Batch fetch group messages by IDs",
			description = "Retrieves a specific list of group chat messages using a batch collection of message UUIDs. " +
					"Verifies user channel visibility, filters out hidden assets, and automatically appends read-cursor metrics."
			)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200", 
					description = "Successfully retrieved message payloads matching the target IDs.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = MucMessageResponse.class))
					),
			@ApiResponse(
					responseCode = "400", 
					description = "Invalid UUID formatting provided in path or payload validation constraints failed.", 
					content = @Content(schema = @Schema(hidden = true))
					),
			@ApiResponse(
					responseCode = "403", 
					description = "Access denied. The authenticated user is not a permitted member of this group chat context.", 
					content = @Content(schema = @Schema(hidden = true))
					)
	})
	public Mono<ResponseEntity<CommonResponse<List<MucMessageResponse>>>> findMessagesByIds(
			@Parameter(description = "The unique UUID of the target MUC group room", required = true, example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
			@PathVariable UUID groupId,
			@RequestBody @Validated GetMessagesByIdsRequest request);


	@Operation(
			summary = "Get earliest retained MUC message boundaries",
			description = "Retrieves the earliest surviving/retained message identifiers (id, messageId, roomId) " +
					"for all active groups the authenticated user belongs to. " +
					"Used by the client application's message engine to truncate or clip local historic " +
					"cache states matching the server's post-retention cleanup limits."
			)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Successfully retrieved active room history synchronization boundaries.",
					content = @Content(
							mediaType = "application/json"
							)
					),
			@ApiResponse(
					responseCode = "401",
					description = "Unauthorized - Missing, expired, or invalid JWT/Session token credentials.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					),
			@ApiResponse(
					responseCode = "500",
					description = "Internal Server Error - Fault in the underlying reactive data stream or database router cluster.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					)
	})
	public Mono<ResponseEntity<CommonResponse<List<MucMessageResponse>>>> getConversationSyncBoundaries();
	
	@Operation(
			summary = "Clear personal chat history timeline window",
			description = "Clears the calling user's personal view of the group chat conversation history timeline. " +
					"Captures the authenticated user's credentials from the security context and registers " +
					"the current server system time as their historical visibility checkpoint boundary. " +
					"Messages generated prior to this timestamp are filtered out during subsequent synchronization loops."
			)
	@ApiResponses(value = {
			@ApiResponse(
					responseCode = "200",
					description = "Timeline threshold boundary advanced successfully.",
					content = @Content(
							mediaType = "application/json"
							)
					),
			@ApiResponse(
					responseCode = "400",
					description = "Bad Request - Invalid UUID string syntax passed in the URL path segment.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					),
			@ApiResponse(
					responseCode = "404",
					description = "Not Found - Specified group space does not exist or the user is not an active participant.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					),
			@ApiResponse(
					responseCode = "500",
					description = "Internal Server Error - Thread starvation or database transaction routing failure.",
					content = @Content(schema = @Schema(implementation = CommonResponse.class))
					)
	})

	public Mono<ResponseEntity<CommonResponse<Boolean>>> clearMemberHistoryTimeline(
			@Parameter(
					name = "groupId",
					description = "The unique UUID of the target group room to clear.",
					required = true,
					example = "4a1b2c3d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"
					)
			@PathVariable UUID groupId);
	
	@Operation(
	        summary = "Hide multiple group messages",
	        description = "Performs an atomic 'Delete for Me' operation on a collection of message IDs within a specific MUC room. Syncs across all the user's active sessions."
	    )
	    @ApiResponses(value = {
	        @ApiResponse(
	            responseCode = "200", 
	            description = "Messages hidden successfully across all user devices.",
	            content = @Content(schema = @Schema(implementation = CommonResponse.class))
	        ),
	        @ApiResponse(
	            responseCode = "400", 
	            description = "Invalid payload formatting, unparseable UUIDs, or missing fields.", 
	            content = @Content
	        ),
	        @ApiResponse(
	            responseCode = "401", 
	            description = "Unauthorized - Missing or expired security context token.", 
	            content = @Content
	        ),
	        @ApiResponse(
	            responseCode = "404", 
	            description = "Group room or targeted messages not found.", 
	            content = @Content
	        )
	    })
	    public Mono<ResponseEntity<CommonResponse<?>>> hideMessages(
	            @Parameter(
	                description = "The unique UUID of the MUC room/group channel", 
	                required = true, 
	                example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
	            )
	            @PathVariable UUID groupId,
	            
	            @io.swagger.v3.oas.annotations.parameters.RequestBody(
	                description = "Payload containing target message identifiers and the originating sessionId",
	                required = true
	            )
	            @RequestBody @Validated HideMessageRequest request);
}
