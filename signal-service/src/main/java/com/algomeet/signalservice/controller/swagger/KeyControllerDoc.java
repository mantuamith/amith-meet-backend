package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.DeviceKeyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Key Retrieval (X3DH)", description = "Endpoints for fetching public key bundles of a recipient to initiate an encrypted session.")
public interface KeyControllerDoc {

    @Operation(
        summary = "Get device keys for a user",
        description = "Returns a list of encrypted device keys for a given user. "
                    + "Optionally filter by a list of device IDs."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Device keys retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                array = @ArraySchema(schema = @Schema(implementation = DeviceKeyResponse.class))
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User or device not found",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CommonResponse.class)
            )
        )
    })
    @GetMapping("/{userKey}")
    public ResponseEntity<CommonResponse<List<DeviceKeyResponse>>> getKeys(
            @Parameter(
                description = "User UUID associated with the device keys",
                required = true,
                example = "e7f3f2b3-2d54-4e13-b51e-92c827bc1e11"
            )
            @PathVariable UUID userKey,

            @Parameter(
                description = "Optional filter: List of device IDs. Example: ?deviceIds=1&deviceIds=2",
                example = "[1, 2, 3]"
            )
            Optional<List<Integer>> deviceIds);
}
