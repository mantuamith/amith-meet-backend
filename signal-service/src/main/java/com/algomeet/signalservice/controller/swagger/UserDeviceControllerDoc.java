package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.DevicePreKeyBundleRequest;
import com.algomeet.signalservice.dto.DevicePreKeyBundleResponse;
import com.algomeet.signalservice.dto.UserDeviceRequest;
import com.algomeet.signalservice.dto.UserDeviceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Devices API", description = "Manage user devices")
public interface UserDeviceControllerDoc {

	@Operation(
			summary = "Create/Register a new user device",
			description = "Registers a new device for the authenticated user",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "Device created/registered successfully",
							content = @Content(schema = @Schema(implementation = UserDeviceResponse.class))
							)
			}
			)
	public ResponseEntity<CommonResponse<UserDeviceResponse>> createDevice(
			@Parameter(description = "Device registration information", required = true)
			@RequestBody UserDeviceRequest request);

	@Deprecated
	@Operation(
			summary = "Get all devices for a user",
			description = "Fetches all registered devices for the authenticated user or the specified userKey",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "List of user devices retrieved successfully",
							content = @Content(schema = @Schema(implementation = UserDeviceResponse.class))
							)
			}
			)
	public ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getDevices(
			@Parameter(description = "Optional user key (UUID) to fetch devices for")
			@RequestParam Optional<UUID> userKey);
	
	@Operation(
			summary = "Get all devices for a user keys",
			description = "Fetches all registered devices for the authenticated user or the userKeys",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "List of user devices retrieved successfully",
							content = @Content(schema = @Schema(implementation = UserDeviceResponse.class))
							)
			}
			)
	public ResponseEntity<CommonResponse<List<UserDeviceResponse>>> getDevicesV3(
			@Parameter(description = "Optional list of user keys (UUID) to fetch devices for")
			@RequestParam Optional<List<UUID>> userKeys);

	@Operation(
			summary = "Update a user device",
			description = "Updates device information for the authenticated user",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "Device updated successfully",
							content = @Content(schema = @Schema(implementation = UserDeviceResponse.class))
							),
					@ApiResponse(
							responseCode = "404",
							description = "Device not found",
							content = @Content(schema = @Schema(implementation = CommonResponse.class))
							)
			}
			)
	public ResponseEntity<CommonResponse<UserDeviceResponse>> updateDevice(
			@Parameter(description = "Device ID to update", required = true)
			@PathVariable Integer deviceId,

			@Parameter(description = "Device update information", required = true)
			@RequestBody UserDeviceRequest request);

	@Operation(
			summary = "Delete a user device",
			description = "Deletes a registered device for the authenticated user",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "Device deleted successfully",
							content = @Content(schema = @Schema(implementation = CommonResponse.class))
							),
					@ApiResponse(
							responseCode = "404",
							description = "Device not found",
							content = @Content(schema = @Schema(implementation = CommonResponse.class))
							)
			}
			)
	public ResponseEntity<CommonResponse<?>> deleteDevice(
			@Parameter(description = "Device ID to delete", required = true)
			@PathVariable Integer deviceId);


	@Operation(
			summary = "Create/Upload Device PreKey Bundle",
			description = "Creates a new pre-key bundle for a specific device belonging to the authenticated user.",
			responses = {
					@ApiResponse(
							responseCode = "200",
							description = "Successfully created the device pre-key bundle",
							content = @Content(schema = @Schema(implementation = DevicePreKeyBundleResponse.class))
							),
					@ApiResponse(
							responseCode = "404",
							description = "Device ID not found",
							content = @Content(schema = @Schema(implementation = CommonResponse.class))
							)
			}
			)
	public ResponseEntity<CommonResponse<DevicePreKeyBundleResponse>> createDevicePreKeyBundle(
			@Parameter(description = "ID of the device") 
			@PathVariable Integer deviceId,

			@io.swagger.v3.oas.annotations.parameters.RequestBody(
					description = "PreKey bundle request payload",
					required = true,
					content = @Content(schema = @Schema(implementation = DevicePreKeyBundleRequest.class))
					)
			@Validated @RequestBody DevicePreKeyBundleRequest request);
}
