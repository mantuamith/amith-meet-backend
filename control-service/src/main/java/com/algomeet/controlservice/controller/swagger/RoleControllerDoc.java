package com.algomeet.controlservice.controller.swagger;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.controlservice.dto.CommonResponse;
import com.algomeet.controlservice.dto.CreateRoleRequest;
import com.algomeet.controlservice.dto.RoleRequest;
import com.algomeet.controlservice.dto.RoleResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Roles", description = "API endpoints for managing roles")
public interface RoleControllerDoc {

    @Operation(
        summary = "Create a new role",
        description = "Creates a new role with given ID and name. Only users with role SA can access this endpoint.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Role created successfully",
                content = @Content(schema = @Schema(implementation = RoleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Role ID or name already exists",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )    
    public ResponseEntity<CommonResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request);

    @Operation(
        summary = "Get a role by ID",
        description = "Fetches role details for the given ID.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Role found",
                content = @Content(schema = @Schema(implementation = RoleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )    
    public ResponseEntity<CommonResponse<RoleResponse>> getRole(
            @Parameter(description = "ID of the role to retrieve") @PathVariable String id) ;

    @Operation(
        summary = "Get all roles",
        description = "Returns a list of all available roles."
    )    
    public ResponseEntity<CommonResponse<List<RoleResponse>>> getAllRoles();

    @Operation(
        summary = "Update an existing role",
        description = "Updates role details for the given ID. Only SA role can update.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Role updated successfully",
                content = @Content(schema = @Schema(implementation = RoleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Role not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )    
    public ResponseEntity<CommonResponse<RoleResponse>> updateRole(
            @Parameter(description = "ID of the role to update") @PathVariable String id,
            @RequestBody RoleRequest request);

    @Operation(
        summary = "Delete a role",
        description = "Deletes the role for the given ID. Only SA role can delete.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Role deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Role not found",
                content = @Content(schema = @Schema(implementation = CommonResponse.class)))
        }
    )    
    public ResponseEntity<CommonResponse<?>> deleteRole(
            @Parameter(description = "ID of the role to delete") @PathVariable String id) ;
}
