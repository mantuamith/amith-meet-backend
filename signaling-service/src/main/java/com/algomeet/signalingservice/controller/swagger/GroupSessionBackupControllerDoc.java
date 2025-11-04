package com.algomeet.signalingservice.controller.swagger;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalingservice.dto.GroupSessionBackupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Group Session Backup", description = "APIs for managing inbound and outbound Matrix group session backups (Megolm).")
public interface GroupSessionBackupControllerDoc {

    @Operation(
        summary = "Save inbound group session backup",
        description = "Saves a new inbound group session backup for the authenticated user. " +
                      "Inbound sessions correspond to Megolm sessions received from other devices.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Inbound group session backup saved successfully",
                    content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class))),
            @ApiResponse(responseCode = "503", description = "Maximum inbound session limit exceeded", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveInboundBackup(
            @Validated @RequestBody GroupSessionBackupRequest request);

    @Operation(
        summary = "Restore all inbound group session backups",
        description = "Retrieves all inbound group session backups for the authenticated user. " +
                      "Used during app startup or session recovery.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Inbound sessions restored successfully",
                    content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getInboundSessions();

    @Operation(
        summary = "Restore a specific inbound group session backup",
        description = "Restores a specific inbound session backup by session ID and ratchet index.",
        parameters = {
            @Parameter(name = "sessionId", description = "Unique group session ID", required = true),
            @Parameter(name = "ratchetIndex", description = "Ratchet index identifying the Megolm state", required = true)
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Inbound session restored successfully",
                    content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Inbound session not found", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getInboundSession(
            @PathVariable String sessionId,
            @PathVariable Integer ratchetIndex);

    @Operation(
        summary = "Delete inbound group session backup",
        description = "Deletes a specific inbound group session backup using session ID and ratchet index.",
        parameters = {
            @Parameter(name = "sessionId", description = "Group session ID", required = true),
            @Parameter(name = "ratchetIndex", description = "Ratchet index of the session to delete", required = true)
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Inbound session deleted successfully", content = @Content),
            @ApiResponse(responseCode = "404", description = "Inbound session not found", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteInboundSession(
            @PathVariable String sessionId,
            @PathVariable int ratchetIndex);

    @Operation(
        summary = "Prune inbound group session backups",
        description = "Deletes older inbound session backups while keeping the most recent ones to limit storage.",
        parameters = {
            @Parameter(name = "sessionId", description = "Group session ID to prune", required = true),
            @Parameter(name = "keepLastN", description = "Number of recent sessions to retain (must be > 0)", required = false)
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Inbound sessions pruned successfully", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid keepLastN parameter", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<?>> pruneInboundBackups(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "100") int keepLastN);

    @Operation(
        summary = "Save outbound group session backup",
        description = "Saves an outbound group session backup used for encrypting messages in Matrix rooms.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Outbound group session backup saved successfully",
                    content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class))),
            @ApiResponse(responseCode = "503", description = "Maximum outbound session limit exceeded", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveOutboundBackup(
            @Validated @RequestBody GroupSessionBackupRequest request);

    @Operation(
        summary = "Restore all outbound group session backups",
        description = "Retrieves all outbound group session backups for the authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Outbound sessions restored successfully",
                    content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getOutboundSessions();

    @Operation(
        summary = "Restore a specific outbound group session backup",
        description = "Restores a specific outbound session backup identified by session ID.",
        parameters = @Parameter(name = "sessionId", description = "Group session ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Outbound session restored successfully",
                    content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Outbound session not found", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getOutboundSession(
            @PathVariable String sessionId);

    @Operation(
        summary = "Delete outbound group session backup",
        description = "Deletes a specific outbound session backup identified by session ID.",
        parameters = @Parameter(name = "sessionId", description = "Group session ID", required = true),
        responses = {
            @ApiResponse(responseCode = "200", description = "Outbound session deleted successfully", content = @Content),
            @ApiResponse(responseCode = "404", description = "Outbound session not found", content = @Content)
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteOutboundSession(@PathVariable String sessionId);

    @Operation(
        summary = "Delete all group session backups",
        description = "Deletes all inbound and outbound group session backups for the authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "All group session backups deleted successfully", content = @Content)
        }
    )
    @DeleteMapping("/all")
    public ResponseEntity<CommonResponse<?>> deleteAllUserSessions();
}