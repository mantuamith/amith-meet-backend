package com.algomeet.mediaservice.controller.swagger;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.mediaservice.dto.BatchMediaDeleteRequest;
import com.algomeet.mediaservice.dto.BatchMediaShareRequest;
import com.algomeet.mediaservice.dto.CommonResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Internal Media API", description = "Service-to-service media operations. Not exposed to the public gateway.")
public interface InternalFileControllerDoc {

    // ========================= SHARE =========================

    @Operation(
        summary = "Share a media file (internal)",
        description = "Called by the chat-service to grant file access to message recipients after delivery.",
        responses = {
            @ApiResponse(responseCode = "200", description = "File shared successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    ResponseEntity<?> share(
            @Parameter(description = "Media ID", required = true)
            @PathVariable UUID mediaId,

            @Parameter(description = "User key of the caller (owner / sharer)", required = true)
            @RequestParam String userKey,

            @Parameter(description = "User keys of the recipients to share the file(s) with. Typically used for one-to-one chats.", required = false,
            example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"660e8400-e29b-41d4-a716-446655440001\"]")
            @RequestParam(required = false) List<String> shareWithUserKeys,

            @Parameter(description = "ID of the group chat to share the file(s) with, adding all group members access simultaneously. Typically used for group chats.")
            @RequestParam(required = false) UUID groupId,
            
            @Parameter(description = "Chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.", required = true)
            @RequestParam UUID messageId
    );
    
    @Operation(
            summary = "Batch share media files",
            description = "Shares multiple media files with a list of users under the context of a specific chat message. Grants read, share, and delete permissions.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Files successfully shared"),
                @ApiResponse(responseCode = "403", description = "Access denied (caller does not have permission to share one or more files)"),
                @ApiResponse(responseCode = "404", description = "One or more media files were not found")
            }
        )
    public ResponseEntity<?> batchShare(
    		@Parameter(description = "User key of the caller (owner / sharer)", required = true)
            @RequestParam String userKey,
            
    		@io.swagger.v3.oas.annotations.parameters.RequestBody(
    				description = "Details required for batch sharing media files", 
    				required = true
    				)
    		@RequestBody @Valid BatchMediaShareRequest request
    		);    

    // ========================= DELETE =========================

    @Operation(
        summary = "Delete a media file (internal)",
        description = "Soft-deletes a media file on behalf of a user. Called by the chat-service when a message is retracted.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Delete successful"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Media not found")
        }
    )
    ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Media ID", required = true)
            @PathVariable UUID mediaId,

            @Parameter(description = "User key of the caller", required = true)
            @RequestParam String userKey,

            @Parameter(description = "Add owner's and additional user keys whose access should also be revoked (e.g., chat participants when retracting a message). Typically used for one-to-one chats.")
            @RequestParam(required = false) Set<String> deleteWithUserKeys,
            
            @Parameter(description = "Chat Group ID the file(s) access to be revoked, removing all group members access simultaneously. Typically used for group chats.")
            @RequestParam(required = false) UUID groupId,
            
            @Parameter(description = "Chat message ID associated with the file attachment. Used to track file references and manage attachment lifecycle.", required = true)
            @RequestParam UUID messageId
    );
    
    @Operation(
            summary = "Batch delete media files", 
            description = "Soft deletes specified media files and queues them for background cleanup if orphaned."
        )
        @ApiResponses(value = {
            @ApiResponse(
                responseCode = "200", 
                description = "Media successfully processed",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            ),
            @ApiResponse(
                responseCode = "404", 
                description = "Media file(s) not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        })
    public ResponseEntity<CommonResponse<?>> batchDelete(
    		@Parameter(description = "User key of the caller", required = true)
            @RequestParam String userKey,
            
    		@RequestBody @Valid BatchMediaDeleteRequest request
    		);
}
