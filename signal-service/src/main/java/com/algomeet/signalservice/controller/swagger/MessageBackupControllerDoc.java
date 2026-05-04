package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.MessageBackupResponse;
import com.algomeet.signalservice.dto.MessageStatusUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

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
    ResponseEntity<CommonResponse<Page<MessageBackupResponse>>> getConversationMessages(
        @Parameter(description = "Peer user key", example = "user_123") String peerKey,
        @Parameter(description = "Cursor (stanzaId). Fetch messages AFTER this ID", example = "01HXYZ...") Optional<String> after,
        @Parameter(description = "Page index", example = "0") int page,
        @Parameter(description = "Page size", example = "50") int size
    );

    @Operation(
        summary = "Sync message events",
        description = "Retrieve message state changes (edited, deleted, read) BEFORE a given stanzaId"
    )
    ResponseEntity<CommonResponse<Page<MessageBackupResponse>>> syncMessages(
        @Parameter(description = "Peer user key", example = "user_123") String peerKey,
        @Parameter(description = "Cursor (stanzaId). Fetch events BEFORE this ID", example = "01HXYZ...") String before,
        @Parameter(description = "Page index", example = "0") int page,
        @Parameter(description = "Page size", example = "50") int size
    );

    @Operation(summary = "Get messages by IDs")
    ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessages(
        @Parameter(description = "List of message IDs") List<String> messageIds
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
        @Parameter(description = "Message ID", example = "msg_123") String messageId
    );

    @Operation(summary = "Update message (full replace)")
    ResponseEntity<CommonResponse<MessageBackupResponse>> updateMessage(
        String messageId,
        @RequestBody MessageBackupDocument request
    );

    @Operation(summary = "Edit message (partial update)")
    ResponseEntity<CommonResponse<MessageBackupResponse>> editMessage(
        String messageId,
        @RequestBody MessageBackupDocument request
    );

    @Operation(summary = "Delete message")
    ResponseEntity<CommonResponse<?>> deleteMessage(String messageId);

    @Operation(summary = "Delete entire conversation")
    ResponseEntity<CommonResponse<?>> deleteByConversation(String peerKey);

    @Operation(summary = "Delete all messages of current user")
    ResponseEntity<CommonResponse<?>> deleteByUserKey();

    @Operation(summary = "Mark message as sent")
    ResponseEntity<CommonResponse<?>> markAsSent(String messageId, MessageStatusUpdateRequest request);

    @Operation(summary = "Mark message as delivered")
    ResponseEntity<CommonResponse<?>> markAsDelivered(String messageId, MessageStatusUpdateRequest request);

    @Operation(summary = "Mark message as read")
    ResponseEntity<CommonResponse<?>> markAsRead(String messageId, MessageStatusUpdateRequest request);

    @Operation(summary = "Mark message as deleted (soft delete)")
    ResponseEntity<CommonResponse<?>> markAsDeleted(String messageId, MessageStatusUpdateRequest request);
}