package com.algomeet.authservice.controller.swagger;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Profiles", description = "APIs for retrieving and updating user profiles")
public interface UserProfileControllerDoc {	

    @GetMapping("/{id}")
    @Operation(
        summary = "Get user profile by ID",
        description = "Retrieve the profile details of a user by their UUID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Profile not found",
                content = @Content(schema = @Schema())),
        }
    )
    public ResponseEntity<CommonResponse<UserProfileResponse>> getProfile(
            @Parameter(description = "UUID of the user", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id);

    @PutMapping("/{id}")
    @Operation(
        summary = "Update user profile",
        description = "Fully update (replace all fields) of a user's profile identified by their UUID.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Profile update request body",
            content = @Content(schema = @Schema(implementation = UserProfileUpdateRequest.class))
        ),
        responses = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
        }
    )
    public ResponseEntity<CommonResponse<UserProfileResponse>> updateProfile(
            @Parameter(description = "UUID of the user", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id,
            @RequestBody UserProfileUpdateRequest request);
}
