package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
@Tag(
	    name = "Group Sender Key Backups API",
	    description = "Endpoints for managing Sender Key Distribution Message backups."
	)

public interface GroupSenderKeyBackupControllerDoc {
    @Operation(summary = "Create a new group sender key backup",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup created successfully",
                content = @Content(schema = @Schema(implementation = GroupSenderKeyBackupResponse.class))),
            @ApiResponse(responseCode = "409", description = "Backup already exists")
        })
    public ResponseEntity<CommonResponse<GroupSenderKeyBackupResponse>> create(
            @Validated @RequestBody GroupSenderKeyBackupRequest request);

    @Operation(summary = "Update an existing group sender key backup",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup updated successfully",
                content = @Content(schema = @Schema(implementation = GroupSenderKeyBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found")
        })
    public ResponseEntity<CommonResponse<GroupSenderKeyBackupResponse>> update(
            @Parameter(description = "Group ID") @PathVariable String groupId,
            @Parameter(description = "Distribution ID") @PathVariable UUID distributionId,
            @Validated @RequestBody GroupSenderKeyBackupUpdateRequest request);

    @Operation(summary = "Get a group sender key backup by groupId and distributionId",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup retrieved successfully",
                content = @Content(schema = @Schema(implementation = GroupSenderKeyBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found")
        })
    public ResponseEntity<CommonResponse<GroupSenderKeyBackupResponse>> get(
            @Parameter(description = "Group ID") @PathVariable String groupId,
            @Parameter(description = "Distribution ID") @PathVariable UUID distributionId);
    
    @Operation(summary = "Get all group sender key backups for the current user",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backups retrieved successfully",
                content = @Content(schema = @Schema(implementation = GroupSenderKeyBackupResponse.class)))
        })
    public ResponseEntity<CommonResponse<List<GroupSenderKeyBackupResponse>>> getByUser();
    
    @Operation(summary = "Get all group sender key backups for a specific group",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backups retrieved successfully",
                content = @Content(schema = @Schema(implementation = GroupSenderKeyBackupResponse.class)))
        })
    public ResponseEntity<CommonResponse<List<GroupSenderKeyBackupResponse>>> getByGroup(
            @Parameter(description = "Group ID") @PathVariable String groupId);

    @Operation(summary = "Delete a group sender key backup",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Backup not found")
        })
    public ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Group ID") @PathVariable String groupId,
            @Parameter(description = "Distribution ID") @PathVariable UUID distributionId);
}
