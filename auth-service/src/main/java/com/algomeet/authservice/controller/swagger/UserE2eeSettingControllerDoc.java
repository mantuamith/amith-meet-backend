package com.algomeet.authservice.controller.swagger;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.UserE2eeSettingRequest;
import com.algomeet.authservice.dto.UserE2eeSettingResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping("/auth/e2ee-user-settings")
@Tag(name = "E2EE User Settings", description = "Manage user-level End-to-End Encryption settings")
public interface UserE2eeSettingControllerDoc {

    /**
     * Get a single E2EE user setting by the authenticated user's key
     */
    @Operation(
        summary = "Get E2EE user setting",
        description = "Retrieves the End-to-End Encryption (E2EE) settings for the currently authenticated user.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "E2EE user setting successfully retrieved",
                content = @Content(schema = @Schema(implementation = UserE2eeSettingResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "E2EE user setting not found",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = @Content
            )
        }
    )
    public ResponseEntity<CommonResponse<UserE2eeSettingResponse>> getById();
    
    /**
     * Create or update an E2EE user setting
     */
    @Operation(
        summary = "Create or update E2EE user setting",
        description = "Creates or updates the End-to-End Encryption (E2EE) settings for the authenticated user.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "E2EE setting request payload",
            required = true,
            content = @Content(schema = @Schema(implementation = UserE2eeSettingRequest.class))
        ),
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "E2EE user setting successfully created or updated",
                content = @Content(schema = @Schema(implementation = UserE2eeSettingResponse.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid request payload",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = @Content
            )
        }
    )
    public ResponseEntity<CommonResponse<UserE2eeSettingResponse>> createOrUpdate(
            @RequestBody UserE2eeSettingRequest request);

    /**
     * Delete an E2EE user setting
     */
    @Operation(
        summary = "Delete E2EE user setting",
        description = "Deletes the End-to-End Encryption (E2EE) setting for the authenticated user.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "E2EE user setting successfully deleted",
                content = @Content(schema = @Schema(implementation = CommonResponse.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "E2EE user setting not found",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = @Content
            )
        }
    )
    public ResponseEntity<?> delete();
}