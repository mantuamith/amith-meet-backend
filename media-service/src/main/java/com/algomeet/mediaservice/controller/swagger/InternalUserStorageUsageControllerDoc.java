package com.algomeet.mediaservice.controller.swagger;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.dto.StorageUsageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Internal User Storage Usage",
    description = "Internal APIs used by services to read and adjust user storage counters. Not intended for public consumption."
)
public interface InternalUserStorageUsageControllerDoc {

    @Operation(
        summary = "Get user storage usage (Internal)",
        description = "Returns the current aggregated storage usage for a given user. Used by internal services such as media or chat processors."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Storage usage retrieved"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal error")
    })
    public ResponseEntity<CommonResponse<StorageUsageResponse>> getUserStorage(
            @Parameter(description = "User UUID", required = true,
                       example = "7f3c3f4c-6a77-4b70-9c9e-1f9d7a6d1abc")
            @PathVariable("userKey") UUID userKey);

    @Operation(
        summary = "Adjust user storage counters (Internal)",
        description = """
            Applies incremental adjustments to a user's storage metrics.
            This endpoint is used by background services after file upload,
            deletion, or chat persistence events.

            Values may be positive (increase) or negative (decrease).
            The service guarantees atomic counter updates.
            """
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Storage counters updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid adjustment payload"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<CommonResponse<StorageUsageResponse>> adjustStorage(
            @Parameter(description = "User UUID", required = true,
                       example = "7f3c3f4c-6a77-4b70-9c9e-1f9d7a6d1abc")
            @PathVariable UUID userKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Adjustment values to apply to the user's storage counters",
                required = true
            )
            @RequestBody StorageUsageAdjustmentRequest request);
}
