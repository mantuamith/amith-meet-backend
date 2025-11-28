package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "One-Time PreKeys API", description = "Operations for creating, retrieving, counting, and deleting one-time pre-keys")
public interface OneTimePreKeyControllerDoc {

    @Operation(
        summary = "Create one-time pre-keys",
        description = "Uploads and saves a list of one-time pre-keys for the specified device",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Pre-keys successfully created",
                content = @Content(schema = @Schema(implementation = OneTimePreKeyResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<List<OneTimePreKeyResponse>>> create(
            @Parameter(description = "Device ID to store pre-keys for", required = true)
            @PathVariable Integer deviceId,

            @Parameter(description = "List of one-time pre-keys to save", required = true)
            @RequestBody OneTimePreKeysRequest request) ;

    @Operation(
        summary = "Get all one-time pre-keys",
        description = "Fetches an all one-time pre-keys for a device and user",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Pre-keys successfully retrieved",
                content = @Content(schema = @Schema(implementation = OneTimePreKeyResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "No available pre-keys or device not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<List<OneTimePreKeyResponse>>> getPrekeys(
            @Parameter(description = "Device ID to fetch pre-key for", required = true)
            @PathVariable Integer deviceId);
    
    @Operation(
        summary = "Get available pre-keys count",
        description = "Returns the count of available one-time pre-keys for the authenticated user's device",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Count retrieved successfully",
                content = @Content(schema = @Schema(implementation = Long.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<Long>> getAvailablePrekeysCount(
            @Parameter(description = "Device ID to count pre-keys for", required = true)
            @PathVariable Integer deviceId);

    @Operation(
        summary = "Delete all one-time pre-keys",
        description = "Deletes all one-time pre-keys for the authenticated user's device",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Pre-keys deleted successfully",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Device not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<?>> deleteAll(
            @Parameter(description = "Device ID to delete pre-keys for", required = true)
            @PathVariable Integer deviceId);
}