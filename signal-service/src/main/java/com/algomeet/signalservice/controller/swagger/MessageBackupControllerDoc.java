package com.algomeet.signalservice.controller.swagger;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.MessageBackupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Message backups API", description = "Operations for saving, retrieving, updating, and deleting message backups")
public interface MessageBackupControllerDoc {

    @Operation(summary = "Save a new chat message backup")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Message backup saved successfully"),
        @ApiResponse(responseCode = "500", description = "Error saving the message backup", content = @Content)
    })
    public ResponseEntity<CommonResponse<?>> saveMessage(
            @RequestBody MessageBackupDocument request);

    @Operation(summary = "Get conversation messages between current user and peer")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successfully retrieved conversation"),
        @ApiResponse(responseCode = "404", description = "Messages not found", content = @Content)
    })
    public ResponseEntity<CommonResponse<Page<MessageBackupResponse>>> getMessagesConversations(
            @Parameter(description = "Peer user key") @PathVariable String peerKey,
            @Parameter(description = "Page number, starting from 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of messages per page") @RequestParam(defaultValue = "50") int size);

    @Operation(summary = "Get messages by a list of message IDs")
    public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessages(
            @Parameter(description = "List of message IDs") @RequestParam List<String> messageIds);

    @Operation(summary = "Get a single message by ID")
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getMessage(
            @Parameter(description = "Message ID") @PathVariable String messageId);

    @Operation(summary = "Update an existing message backup")
    public ResponseEntity<CommonResponse<MessageBackupResponse>> updateMessage(
            @Parameter(description = "Message ID") @PathVariable String messageId,
            @RequestBody MessageBackupDocument request);

    @Operation(summary = "Delete a message by ID")
    public ResponseEntity<CommonResponse<?>> deleteMessage(
            @Parameter(description = "Message ID") @PathVariable String messageId);

    @Operation(summary = "Delete all messages in a conversation with a peer")
    public ResponseEntity<CommonResponse<?>> deleteByConversation(
            @Parameter(description = "Peer user key") @PathVariable String peerKey);

    @Operation(summary = "Delete all messages of the current user")
    public ResponseEntity<CommonResponse<?>> deleteByUserKey();
}