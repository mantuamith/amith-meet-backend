package com.algomeet.signalingservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserAccountBackupRequest;
import com.algomeet.signalingservice.dto.UserAccountBackupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "User Account Backup",
    description = "Endpoints for managing Matrix user account (OLM device) backups."
)
public interface UserAccountBackupControllerDoc {

    /**
     * Saves a new user account backup for the authenticated user.
     *
     * @param request contains the encrypted account data and backup metadata
     * @return a success response if saved, or a conflict if it already exists
     */
    @Operation(
        summary = "Save user account backup",
        description = "Stores a new encrypted Matrix OLM account backup for the authenticated user and device.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup successfully saved",
                    content = @Content(schema = @Schema(implementation = UserAccountBackupResponse.class))),
            @ApiResponse(responseCode = "302", description = "Backup already exists",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> saveBackup(
            @Validated @RequestBody UserAccountBackupRequest request);

    /**
     * Updates an existing user account backup.
     *
     * @param request contains the updated encrypted account data and metadata
     * @return a success response if updated, or not found if no backup exists
     */
    @Operation(
        summary = "Update user account backup",
        description = "Updates an existing Matrix OLM account backup for the authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup successfully updated",
                    content = @Content(schema = @Schema(implementation = UserAccountBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> updateBackup(
            @Validated @RequestBody UserAccountBackupRequest request);

    /**
     * Retrieves a user account backup by device ID.
     *
     * @param deviceId the device identifier used to locate the backup
     * @return a response containing the user backup if found, otherwise 404
     */
    @Operation(
        summary = "Retrieve user account backup",
        description = "Fetches the encrypted user account backup associated with a specific device ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup found",
                    content = @Content(schema = @Schema(implementation = UserAccountBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> getBackup(
            @Parameter(description = "Device ID for which to retrieve the backup") 
            @RequestParam("deviceId") String deviceId);
    
    /**
     * Deletes a specific user account backup by device ID.
     *
     * @param deviceId the device ID whose backup will be deleted
     * @return a success response if deleted, or 404 if not found
     */
    @Operation(
        summary = "Delete user account backup",
        description = "Removes the encrypted user account backup associated with a given device ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup successfully deleted",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteBackup(
            @Parameter(description = "Device ID of the backup to delete")
            @RequestParam("deviceId") String deviceId);

    /**
     * Deletes all backups for the authenticated user.
     *
     * @return a success response upon deletion
     */
    @Operation(
        summary = "Delete all user account backups",
        description = "Removes all encrypted Matrix OLM account backups for the authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "All backups successfully deleted",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteAllUserBackup();
}
