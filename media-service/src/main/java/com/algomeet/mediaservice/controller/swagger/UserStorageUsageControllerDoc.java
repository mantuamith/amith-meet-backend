package com.algomeet.mediaservice.controller.swagger;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.StorageUsageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Storage Usage", description = "APIs for retrieving media and chat storage usage of users")
@SecurityRequirement(name = "bearerAuth")
public interface UserStorageUsageControllerDoc {

    @Operation(
        summary = "Get current user's storage usage",
        description = "Returns the authenticated user's media and chat storage consumption"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Storage usage retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponse<StorageUsageResponse>> getUserStorage();

    @Operation(
        summary = "Get specific user's storage usage (Admin only)",
        description = "Allows ADMIN or SA roles to retrieve storage usage details of a specific user."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Storage usage retrieved successfully"),
        @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN or SA role"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponse<StorageUsageResponse>> getUserStorage(
            @Parameter(
                description = "Unique UUID of the user",
                example = "7f3c3f4c-6a77-4b70-9c9e-1f9d7a6d1abc",
                required = true
            )
            @PathVariable UUID userKey);
}
