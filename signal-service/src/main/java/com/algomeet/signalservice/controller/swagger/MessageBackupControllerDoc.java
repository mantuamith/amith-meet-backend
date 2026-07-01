package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.MessageBackupRequest;
import com.algomeet.signalservice.dto.MessageBackupResponse;
import com.algomeet.signalservice.dto.MessageBackupUpdateRequest;
import com.algomeet.signalservice.dto.MessageStatusUpdateRequest;
import com.algomeet.signalservice.dto.GetMessagesByIdsRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Chat Message Backup", description = "APIs for managing encrypted chat message backups and synchronization")
public interface MessageBackupControllerDoc {

	@Operation(
		    summary = "Save message backup",
		    description = "Stores a new encrypted chat message backup for synchronization across devices"
		)
		@ApiResponses(value = {
		    @ApiResponse(
		        responseCode = "200",
		        description = "Message saved successfully",
		        content = @Content(
		            mediaType = "application/json",
		            schema = @Schema(implementation = CommonResponse.class)
		        )
		    ),
		    @ApiResponse(
		        responseCode = "400",
		        description = "Invalid request payload"
		    ),
		    @ApiResponse(
		        responseCode = "500",
		        description = "Failed to save message"
		    )
		})

		ResponseEntity<CommonResponse<?>> saveMessage(
		        @Parameter(
		            description = "Message backup payload",
		            required = true
		        )
		        @io.swagger.v3.oas.annotations.parameters.RequestBody(
		            required = true,
		            description = "Encrypted message backup data",
		            content = @Content(
		                schema = @Schema(implementation = MessageBackupRequest.class)
		            )
		        )
		        @Validated @RequestBody MessageBackupRequest request);

    @Operation(
        summary = "Get conversation messages",
        description = "Retrieve paginated conversation messages (oldest → newest). Supports cursor-based pagination using 'after'."
    )
    ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getConversationMessages(
        @Parameter(description = "Peer user key", example = "9640b033-3c2a-...") UUID peerKey,
        @Parameter(description = "Cursor (stanzaId). Fetch messages BEFORE this ID", example = "019e4c0a...") UUID before,
        @Parameter(description = "Cursor (stanzaId). Fetch messages AFTER this ID", example = "019e4c0a...") UUID after,
        @Parameter(description = "Page index", example = "0") int page,
        @Parameter(description = "Page size", example = "50") int size
    );

    @Operation(
        summary = "Get conversation message updates",
        description = "Retrieve message state changes (edited, deleted, read) up to and including the specified stanzaId."
    )
    ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessageUpdates(
        @Parameter(description = "Peer user key", example = "9640b033-3c2a-...") UUID peerKey,
        @Parameter(description = "Cursor (stanzaId). Fetch events up to and including the specified stanzaId.", example = "019e4c0a...") UUID uptoStanzaId,
        @Parameter(description = "Page index", example = "0") int page,
        @Parameter(description = "Page size", example = "50") int size
    );
    
    @Deprecated
    @Operation(
            summary = "Sync message events",
            description = "Retrieve message state changes (edited, deleted, read) BEFORE a given stanzaId"
        )
        ResponseEntity<CommonResponse<List<MessageBackupResponse>>> syncMessages(
            @Parameter(description = "Peer user key", example = "9640b033-3c2a-...") UUID peerKey,
            @Parameter(description = "Cursor (stanzaId). Fetch events BEFORE this ID", example = "019e4c0a...") UUID before,
            @Parameter(description = "Page index", example = "0") int page,
            @Parameter(description = "Page size", example = "50") int size
        );

    @Operation(summary = "Get messages by IDs")
    ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessages(
        @Parameter(description = "List of message IDs") List<UUID> messageIds
    );

    @Operation(summary = "Get conversation contacts")
    ResponseEntity<CommonResponse<List<String>>> getConversationContacts();

    @Operation(
        summary = "Get message by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Message found"),
            @ApiResponse(responseCode = "404", description = "Message not found")
        }
    )
    ResponseEntity<CommonResponse<MessageBackupResponse>> getMessage(
        @Parameter(description = "Message ID", example = "e2abff51-d2b8-...") UUID messageId
    );

    @Operation(summary = "Update message (full replace)")
    ResponseEntity<CommonResponse<MessageBackupResponse>> updateMessage(
    	UUID messageId,
        @RequestBody MessageBackupUpdateRequest request
    );

    @Operation(
    		summary = "Delete entire conversation",
    		description = "Deletes an entire conversation up to an optionally specified stanza ID. " +
    				"This will trigger background cleanup tasks for associated media files."
    		)
    @ApiResponses(value = {
    		@ApiResponse(
    				responseCode = "200", 
    				description = "Conversation deletion initiated successfully",
    				content = @Content(schema = @Schema(implementation = CommonResponse.class))
    				),
    		@ApiResponse(responseCode = "400", description = "Invalid request parameters"),
    		@ApiResponse(responseCode = "401", description = "Unauthorized access"),
    		@ApiResponse(responseCode = "500", description = "Internal server error")
    })

    ResponseEntity<CommonResponse<?>> deleteByConversation(
    		@Parameter(description = "The unique key identifying the peer in the conversation", required = true)
    		UUID peerKey,

    		@Parameter(description = "The UUID of the last processed stanza. If provided, messages up to this ID will be deleted.", required = false)
    		@RequestParam(name = "lastStanzaId", required = false) UUID lastStanzaId
    		);

    @Operation(summary = "Delete all messages of current user")
    ResponseEntity<CommonResponse<?>> deleteByUserKey();

    @Operation(summary = "Mark message(s) as sent")
    ResponseEntity<CommonResponse<?>> markAsSent(MessageStatusUpdateRequest request);

    @Operation(summary = "Mark message(s) as delivered")
    ResponseEntity<CommonResponse<?>> markAsDelivered(MessageStatusUpdateRequest request);

    @Operation(
    	    summary = "Mark a message(s) as read",
    	    description = "Updates the status of a specific message(s) to 'read', with an optional timestamp.",
    	    responses = {
    	        @ApiResponse(
    	            responseCode = "200", 
    	            description = "Message(s) successfully marked as read",
    	            content = @Content(schema = @Schema(implementation = CommonResponse.class))
    	        ),
    	        @ApiResponse(
    	            responseCode = "404", 
    	            description = "Message(s) not found",
    	            content = @Content(schema = @Schema(implementation = CommonResponse.class))
    	        )
    	    }
    	)
    	public ResponseEntity<CommonResponse<?>> markAsRead(
    	        @Parameter(
    	            description = "The unique UUID of the message to be marked as read. "
    	            		+ "It can mark multiple messages as read if the current message ID is greater than the previously acknowledged message ID.", 
    	            required = true, 
    	            example = "123e4567-e89b-12d3-a456-426614174000"
    	        )
    	        @PathVariable UUID messageId,
    	        
    	        @Parameter(
    	            description = "Optional epoch timestamp (in milliseconds) when the message was read. If omitted, the current server time is used.", 
    	            required = false, 
    	            example = "1717000000000"
    	        )
    	        @RequestParam(value = "date", required = false) Long date);
    
    @Operation(summary = "Mark message(s) as retracted (soft delete by sender)")
    ResponseEntity<CommonResponse<?>> markAsRetracted(MessageStatusUpdateRequest request);
    
    @Operation(summary = "Mark message(s) as hidden (soft delete by sender)")
    ResponseEntity<CommonResponse<?>> markAsHidden(MessageStatusUpdateRequest request);
    
    @Operation(
    		summary = "Get last sent message",
    		description = "Retrieves the metadata of the last message sent by the currently authenticated user to the specified peer. Returns null within the data wrapper if no messages have been sent yet.",
    		responses = {
    				@ApiResponse(
    						responseCode = "200",
    						description = "Successfully retrieved last sent message state.",
    						content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
    						),
    				@ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing authentication token.", content = @Content),
    				@ApiResponse(responseCode = "400", description = "Bad Request - Invalid UUID format for peerKey.", content = @Content)
    		}
    		)
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getConversationLastSent(
    		@Parameter(description = "The unique UUID identifier of the other chat participant", required = true, example = "a6c905b2-3e21-4b10-8e14-637bc39d0124")
    		@PathVariable UUID peerKey);

    @Operation(
    		summary = "Get last received message",
    		description = "Retrieves the metadata of the last message authored by the specified peer and received by the currently authenticated user. Returns null within the data wrapper if no messages have been received yet.",
    		responses = {
    				@ApiResponse(
    						responseCode = "200",
    						description = "Successfully retrieved last received message state.",
    						content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
    						),
    				@ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing authentication token.", content = @Content),
    				@ApiResponse(responseCode = "400", description = "Bad Request - Invalid UUID format for peerKey.", content = @Content)
    		}
    		)
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getConversationLastReceived(
    		@Parameter(description = "The unique UUID identifier of the chat participant who authored the message", required = true, example = "e2b349d4-1a73-45bb-b302-123456789abc")
    		@PathVariable UUID peerKey);
    

    @Operation(
    		summary = "Apply message retention policy",
    		description = "Configures the number of days messages are retained in a 1-on-1 chat with a peer. " +
    				"Triggers background updates. Returns a conflict status if a retention update task is already executing."
    		)
    @ApiResponses(value = {
    		@ApiResponse(
    				responseCode = "200", 
    				description = "Retention policy successfully scheduled or updated.",
    				content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
    				),
    		@ApiResponse(
    				responseCode = "409", 
    				description = "Conflict. A message retention calculation or lifecycle sync update is already running for this thread.", 
    				content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommonResponse.class))
    				),
    		@ApiResponse(
    				responseCode = "400", 
    				description = "Invalid UUID formatting provided in path or parameter constraint violations.", 
    				content = @Content(schema = @Schema(hidden = true))
    				)
    })
    public ResponseEntity<CommonResponse<?>> applyMessageRetentionPolicy(
    		@Parameter(description = "The unique UUID of the peer user in the 1-on-1 chat thread", required = true, example = "4a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d")
    		@PathVariable UUID peerKey,
    		@Parameter(description = "Number of days to keep messages active before policy auto-purge executes", required = true, example = "30")
    		@RequestParam Integer messageRetentionDays);

    @Operation(
    		summary = "Batch fetch direct messages by IDs",
    		description = "Retrieves an explicit collection of historical backup messages from a chat thread using an array of message UUIDs."
    		)
    @ApiResponses(value = {
    		@ApiResponse(
    				responseCode = "200", 
    				description = "Successfully compiled historical message payloads.",
    				content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageBackupResponse.class))
    				),
    		@ApiResponse(
    				responseCode = "400", 
    				description = "Invalid validation rules or payload formatting constraints violated.", 
    				content = @Content(schema = @Schema(hidden = true))
    				)
    })
    public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> findMessagesByIds(
    		@Parameter(description = "The unique UUID of the peer user in the 1-on-1 chat thread", required = true, example = "4a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d")
    		@PathVariable UUID peerKey, // Changed placeholder name logic to match URI safely
    		@RequestBody @Validated GetMessagesByIdsRequest request);
}