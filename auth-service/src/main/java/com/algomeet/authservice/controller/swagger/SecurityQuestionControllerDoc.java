package com.algomeet.authservice.controller.swagger;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.SecurityQuestionRequest;
import com.algomeet.authservice.dto.SecurityQuestionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Security Questions", description = "APIs for managing security questions")
public interface SecurityQuestionControllerDoc {
    // ---------- Create ----------
    @Operation(
            summary = "Create Security Question",
            description = "Creates a new security question if the ID does not already exist.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security question created successfully",
                            content = @Content(schema = @Schema(implementation = SecurityQuestionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Security question ID already exists",
                            content = @Content(schema = @Schema(implementation = CommonResponse.class)))
            }
    )
    public ResponseEntity<CommonResponse<SecurityQuestionResponse>> create(
            @Valid @RequestBody SecurityQuestionRequest request);

    // ---------- Get by ID ----------
    @Operation(
            summary = "Get Security Question by ID",
            description = "Fetches a security question by its unique identifier.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security question found",
                            content = @Content(schema = @Schema(implementation = SecurityQuestionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Security question not found")
            }
    )
    public ResponseEntity<CommonResponse<SecurityQuestionResponse>> getById(
            @Parameter(description = "Security Question ID", required = true)
            @PathVariable String id);

    // ---------- Get All ----------
    @Operation(
            summary = "Get All Security Questions",
            description = "Fetches all security questions available.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "List of security questions",
                            content = @Content(schema = @Schema(implementation = SecurityQuestionResponse.class)))
            }
    )
    public ResponseEntity<CommonResponse<List<SecurityQuestionResponse>>> getAll();

    // ---------- Update ----------
    @Operation(
            summary = "Update Security Question",
            description = "Updates a security question by replacing its details.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security question updated successfully",
                            content = @Content(schema = @Schema(implementation = SecurityQuestionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Security question not found")
            }
    )
    public ResponseEntity<CommonResponse<SecurityQuestionResponse>> update(
            @Parameter(description = "Security Question ID", required = true)
            @PathVariable String id,
            @RequestBody SecurityQuestionRequest request);

    // ---------- Delete ----------
    @Operation(
            summary = "Delete Security Question",
            description = "Deletes a security question by its ID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security question deleted successfully"),
                    @ApiResponse(responseCode = "404", description = "Security question not found")
            }
    )
    public ResponseEntity<CommonResponse<?>> delete(
            @Parameter(description = "Security Question ID", required = true)
            @PathVariable String id);
}
