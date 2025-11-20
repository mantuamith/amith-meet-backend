package com.algomeet.authservice.controller.swagger;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.SearchUsersFilter;
import com.algomeet.authservice.dto.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Users", description = "APIs for managing and retrieving user information")
public interface UserControllerDoc {

    @Operation(
        summary = "Search users",
        description = "Search for users based on filter criteria. Only accessible to Super Admin and Admin roles.",
        parameters = {
            @Parameter(name = "username", description = "Filter by username (optional)", required = false),
            @Parameter(name = "email", description = "Filter by email (optional)", required = false),
            @Parameter(name = "status", description = "Filter by account status (optional)", required = false)
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
        }
    )
    public ResponseEntity<? extends CommonResponse<?>> getUsers(SearchUsersFilter filter);

    @PreAuthorize("hasAnyRole('SA','ADMIN')")
    @Operation(
        summary = "Get user by ID",
        description = "Retrieve a user by their numeric database ID. Only accessible to Super Admin and Admin roles.",
        responses = {
            @ApiResponse(responseCode = "200", description = "User found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
        }
    )
    public ResponseEntity<CommonResponse<UserResponse>> findById(
            @Parameter(description = "User ID", example = "1001") @PathVariable Long id);

    @Operation(
        summary = "Get user by User Key",
        description = "Retrieve a user by their UUID-based userKey. Only accessible to Super Admin and Admin roles.",
        responses = {
            @ApiResponse(responseCode = "200", description = "User found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Insufficient permissions")
        }
    )
    public ResponseEntity<CommonResponse<UserResponse>> getUserByUserKey(
            @Parameter(description = "UUID user key", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID userKey);
}