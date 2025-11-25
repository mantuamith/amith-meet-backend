package com.algomeet.signalservice.controller.swagger;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Kyber PreKey API", description = "Operations for retrieving and updating Kyber pre-keys")
public interface KyberPreKeyControllerDoc {

    @Operation(
        summary = "Retrieve Kyber PreKey",
        description = "Returns the Kyber PreKey for the specified device and user",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "PreKey successfully retrieved",
                content = @Content(schema = @Schema(implementation = KyberPreKeyResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "PreKey not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<KyberPreKeyResponse>> retrieve(
            @Parameter(description = "Device ID", required = true)
            @PathVariable Integer deviceId,

            @Parameter(description = "Optional User Key (UUID). Defaults to authenticated user.")
            Optional<UUID> userKey);
    
    @Operation(
        summary = "Update Kyber PreKey",
        description = "Updates the Kyber PreKey for a device belonging to the authenticated user",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "PreKey updated successfully",
                content = @Content(schema = @Schema(implementation = KyberPreKeyResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "PreKey record not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            )
        }
    )
    public ResponseEntity<CommonResponse<KyberPreKeyResponse>> update(
            @Parameter(description = "Device ID", required = true)
            @PathVariable Integer deviceId,

            @Parameter(description = "New PreKey data", required = true)
            @RequestBody KyberPreKeyRequest request);
}