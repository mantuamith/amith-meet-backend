package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Group Session Backups API",
    description = "Manage encrypted group session backups per user/device"
)
public interface GroupSessionBackupControllerDoc {

    @Operation(
        summary = "Save a group session backup",
        description = "Stores a new encrypted sender-key group session backup for the authenticated user.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Backup saved successfully",
                content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class))
            )
        }
    )

    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> saveBackup(
            @Validated @RequestBody GroupSessionBackupRequest request);

    // ----------------------------------------------------------------------
    @Operation(
        summary = "Get all backups for user",
        description = "Returns all stored group session backups for the authenticated user.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "List of backups retrieved",
                content = @Content(schema = @Schema(implementation = GroupSessionBackupResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getBackups();

    // ----------------------------------------------------------------------
    @Operation(
        summary = "Get a group session backup by groupId and distributionId",
        description = "Retrieves a single backup record for a specific group and distribution ID.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Backup found"
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Backup not found"
            )
        }
    )
    public ResponseEntity<CommonResponse<GroupSessionBackupResponse>> getBackupByDistribution(
            @Parameter(description = "Group identifier")
            @PathVariable UUID groupId,

            @Parameter(description = "Sender key distribution ID")
            @PathVariable UUID distributionId,
            
            @Parameter(description = "true = inbound, false = outbound")
			@PathVariable boolean isInbound);

    // ----------------------------------------------------------------------
    @Operation(
        summary = "Get group session backups for a device",
        description = "Returns all group session backups created by a specific device ID.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Device backups found"
            )
        }
    )
    public ResponseEntity<CommonResponse<List<GroupSessionBackupResponse>>> getBackupByDevice(
            @Parameter(description = "Device ID of the sender")
            @PathVariable Integer deviceId);


    // ----------------------------------------------------------------------
    @Operation(
        summary = "Delete a specific group session backup",
        description = "Removes a backup entry identified by groupId and distributionId.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup deleted"),
            @ApiResponse(responseCode = "404", description = "Backup not found")
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteBackup(
            @Parameter(description = "Group identifier")
            @PathVariable UUID groupId,

            @Parameter(description = "Sender key distribution ID")
            @PathVariable UUID distributionId,

            @Parameter(description = "true = inbound, false = outbound")
			@PathVariable boolean isInbound);

    // ----------------------------------------------------------------------
    @Operation(
        summary = "Delete backups by device",
        description = "Deletes all backup entries created by the specified device.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Device backups deleted"),
            @ApiResponse(responseCode = "404", description = "No backups found for device")
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteBackupByDevice(
            @Parameter(description = "Device ID")
            @PathVariable Integer deviceId);

    // ----------------------------------------------------------------------
    @Operation(
        summary = "Delete all backups for user",
        description = "Deletes all group session backups belonging to the authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "All backups deleted"),
            @ApiResponse(responseCode = "404", description = "No backups found")
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteAllBackups();
}