package com.algomeet.controlservice.controller.swagger;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.controlservice.dto.CommonResponse;
import com.algomeet.controlservice.dto.RegisterTenantRequest;
import com.algomeet.controlservice.dto.TenantRequest;
import com.algomeet.controlservice.dto.TenantResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Tenants", description = "API endpoints for managing tenants")
public interface TenantControllerDoc {
	
	@Operation(
		summary = "Get all tenants",
		description = "Retrieves a list of all tenants. Only SA role can access this endpoint.",
		responses = {
			@ApiResponse(responseCode = "200", description = "List of tenants retrieved",
				content = @Content(schema = @Schema(implementation = TenantResponse.class)))
		}
	)	
	public ResponseEntity<CommonResponse<List<TenantResponse>>> getAllTenants();

	@Operation(
		summary = "Get tenant by ID",
		description = "Fetch tenant details by ID. If ID is 0, retrieves tenant ID from the current user session.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Tenant found",
				content = @Content(schema = @Schema(implementation = TenantResponse.class))),
			@ApiResponse(responseCode = "404", description = "Tenant not found",
				content = @Content(schema = @Schema(implementation = CommonResponse.class)))
		}
	)	
	public ResponseEntity<CommonResponse<TenantResponse>> getTenantById(
			@Parameter(description = "Tenant ID. Use 0 to fetch tenant from user session.") 
			@PathVariable Integer id);

	@Operation(
		summary = "Create tenant",
		description = "Registers a new tenant. Only SA role can create tenants.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Tenant created successfully",
				content = @Content(schema = @Schema(implementation = TenantResponse.class))),
			@ApiResponse(responseCode = "400", description = "Tenant ID already exists",
				content = @Content(schema = @Schema(implementation = CommonResponse.class)))
		}
	)	
	public ResponseEntity<CommonResponse<TenantResponse>> createTenant(
			@Valid @RequestBody RegisterTenantRequest request);

	@Operation(
		summary = "Update tenant",
		description = "Updates details of an existing tenant. Allowed for SA and ADMIN roles who are owners of the tenant.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Tenant updated successfully",
				content = @Content(schema = @Schema(implementation = TenantResponse.class))),
			@ApiResponse(responseCode = "404", description = "Tenant not found",
				content = @Content(schema = @Schema(implementation = CommonResponse.class)))
		}
	)	
	public ResponseEntity<CommonResponse<TenantResponse>> updateTenant(
			@Parameter(description = "Tenant ID to update") @PathVariable Integer id,
			@RequestBody TenantRequest request);

	@Operation(
		summary = "Delete tenant",
		description = "Deletes a tenant by ID. Only SA role can perform this action.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Tenant deleted successfully"),
			@ApiResponse(responseCode = "404", description = "Tenant not found",
				content = @Content(schema = @Schema(implementation = CommonResponse.class)))
		}
	)	
	public ResponseEntity<CommonResponse<TenantResponse>> deleteTenant(
			@Parameter(description = "Tenant ID to delete") @PathVariable Integer id);
}
