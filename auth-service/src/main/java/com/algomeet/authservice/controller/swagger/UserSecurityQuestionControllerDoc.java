package com.algomeet.authservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.UserSecurityQuestionRequest;
import com.algomeet.authservice.dto.UserSecurityQuestionResponse;
import com.algomeet.authservice.dto.VerifySecurityQuestionRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;


@Tag(name = "User Security Questions", description = "APIs for managing user security questions and verifying answers")
public interface UserSecurityQuestionControllerDoc {
   
    @Operation(
    		summary = "Add a security question for a user",
    		description = "Create a new user security question entry linked to a user profile ID.",
    		responses = {
    				@ApiResponse(responseCode = "200", description = "Security question added successfully",
    						content = @Content(schema = @Schema(implementation = CommonResponse.class))),
    				@ApiResponse(responseCode = "409", description = "Security question already exists for this user")
    		}
    		)
    public ResponseEntity<CommonResponse<UserSecurityQuestionResponse>> create(
    		@Valid @RequestBody UserSecurityQuestionRequest request);

    @Operation(
        summary = "Batch add user security questions",
        description = "Create multiple security questions for a user profile in one request.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Questions added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "One or more questions already exist for this user")
        }
    )
    public ResponseEntity<CommonResponse<List<UserSecurityQuestionResponse>>> create(
            @RequestBody List<UserSecurityQuestionRequest> requests);

    @Operation(
        summary = "Get all security questions for a user",
        description = "Retrieve all security questions linked to a specific user profile.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Questions retrieved successfully")
        }
    )
    public ResponseEntity<CommonResponse<List<UserSecurityQuestionResponse>>> getByUserProfileId(
            @Parameter(description = "User Profile UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID userProfileId);
    
    @Operation(
        summary = "Delete all security questions for a user",
        description = "Remove all stored security questions for a given user profile ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Security questions deleted successfully")
        }
    )
    public ResponseEntity<CommonResponse<UserSecurityQuestionResponse>> deleteByUserProfileId(
            @PathVariable UUID userProfileId);
       
    @Operation(
        summary = "Get a specific user security question",
        description = "Retrieve a security question for a user by profile ID and question ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Security question retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Security question not found")
        }
    )
    public ResponseEntity<CommonResponse<UserSecurityQuestionResponse>> getByUserProfileIdAndQuestionId(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId);
        
    @Operation(
        summary = "Verify a security question answer",
        description = "Check if the provided answer to a security question is correct for a given user profile.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Verification successful"),
            @ApiResponse(responseCode = "404", description = "Security question not found")
        }
    )
    public ResponseEntity<?> verifyAnswer(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId,
            @Valid @RequestBody VerifySecurityQuestionRequest request);
}