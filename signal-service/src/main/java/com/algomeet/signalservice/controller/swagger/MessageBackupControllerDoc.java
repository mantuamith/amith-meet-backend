package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.MessageBackupResponse;
import com.algomeet.signalservice.dto.MessageStatusUpdateRequest;

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
		                schema = @Schema(implementation = MessageBackupDocument.class)
		            )
		        )
		        @Validated @RequestBody MessageBackupDocument request);

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
        @RequestBody MessageBackupDocument request
    );

    @Operation(summary = "Edit message (partial update), do not use this if you need the history of the original message or previous message updates.")
    ResponseEntity<CommonResponse<MessageBackupResponse>> editMessage(
    	UUID messageId,
        @RequestBody MessageBackupDocument request
    );

    @Operation(summary = "Delete one or more messages")
    ResponseEntity<CommonResponse<?>> deleteMessages(List<UUID> messageIds);

    @Operation(summary = "Delete entire conversation")
    ResponseEntity<CommonResponse<?>> deleteByConversation(UUID peerKey);

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
}