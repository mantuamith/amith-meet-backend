package com.algomeet.signalservice.controller.swagger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Signed PreKeys API", description = "Operations for updating and retrieving signed pre-keys")
public interface SignedPreKeyControllerDoc {

    @Operation(
        summary = "Retrieve signed pre-key",
        description = "Fetches the signed pre-key for the specified device and user. Defaults to the authenticated user if userKey is not provided.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Signed pre-key retrieved successfully",
                content = @Content(schema = @Schema(implementation = SignedPreKeyResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Signed pre-key not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<SignedPreKeyResponse>> get(
            @Parameter(description = "Device ID to fetch the signed pre-key for", required = true)
            @PathVariable Integer deviceId,

            @Parameter(description = "User key (UUID). Defaults to authenticated user")
            @RequestParam Optional<UUID> userKey
    );

    @Operation(
        summary = "Update signed pre-key",
        description = "Updates the signed pre-key for the authenticated user's device",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Signed pre-key updated successfully",
                content = @Content(schema = @Schema(implementation = SignedPreKeyResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Signed pre-key not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<SignedPreKeyResponse>> update(
            @Parameter(description = "Device ID to update the signed pre-key for", required = true)
            @PathVariable Integer deviceId,

            @Parameter(description = "Signed pre-key data to update", required = true)
            @RequestBody SignedPreKeyRequest request
    );
}