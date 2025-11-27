package com.algomeet.signalservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupRequest;
import com.algomeet.signalservice.dto.DeviceKeyBackupResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "User Device Key Backups API",
    description = "Endpoints for managing Signal device key backups."
)
public interface DeviceKeyBackupControllerDoc {

    /**
     * Saves a new user account backup for the authenticated user.
     *
     * @param request contains the encrypted account data and backup metadata
     * @return a success response if saved, or a conflict if it already exists
     */
    @Operation(
        summary = "Save user device key backup",
        description = "Stores a new encrypted Signal device key backup for the authenticated user and device.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup successfully saved",
                    content = @Content(schema = @Schema(implementation = DeviceKeyBackupResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<DeviceKeyBackupResponse>> saveBackup(
            @Validated @RequestBody DeviceKeyBackupRequest request);  
    
    
    /**
     * Update a user device key backup for the authenticated user.
     *
     * @param request contains the encrypted account data and backup metadata
     * @return a success response if saved, or a conflict if it already exists
     */
    @Operation(
        summary = "Update user device key backup",
        description = "Stores a new encrypted Signal device key backup for the authenticated user and device.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup successfully updated",
                    content = @Content(schema = @Schema(implementation = DeviceKeyBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found",
            content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<DeviceKeyBackupResponse>> updateBackup(
    		@PathVariable("deviceId") Integer deviceId,
            @Validated @RequestBody DeviceKeyBackupUpdateRequest request); 

    /**
     * Retrieves a user account backup by device ID.
     *
     * @param deviceId the device identifier used to locate the backup
     * @return a response containing the user backup if found, otherwise 404
     */
    @Operation(
        summary = "Retrieve user device key backup",
        description = "Fetches the encrypted user device key backup associated with a specific device ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Backup found",
                    content = @Content(schema = @Schema(implementation = DeviceKeyBackupResponse.class))),
            @ApiResponse(responseCode = "404", description = "Backup not found",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<DeviceKeyBackupResponse>> getBackup(
            @Parameter(description = "Device ID for which to retrieve the backup") 
            @PathVariable("deviceId") Integer deviceId);
    
    /**
     * Deletes a specific user account backup by device ID.
     *
     * @param deviceId the device ID whose backup will be deleted
     * @return a success response if deleted, or 404 if not found
     */
    @Operation(
        summary = "Delete user device key backup",
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
            @PathVariable("deviceId") Integer deviceId);

    /**
     * Deletes all backups for the authenticated user.
     *
     * @return a success response upon deletion
     */
    @Operation(
        summary = "Delete all user device key backups",
        description = "Removes all encrypted Signal device key backups for the authenticated user.",
        responses = {
            @ApiResponse(responseCode = "200", description = "All backups successfully deleted",
                    content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteAllUserBackup();
}
