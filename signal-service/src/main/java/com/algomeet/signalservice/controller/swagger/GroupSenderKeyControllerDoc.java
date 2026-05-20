package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Group Sender Keys API", description = "Sender key distribution for secure group messaging")
public interface GroupSenderKeyControllerDoc {

	/**
	 * Sender device uploads SKDM
	 */

	@Operation(
			summary = "Upload Sender Key Distribution Message (SKDM)",
			description = "Sender device uploads a SKDM for a specific group."
			)
	@ApiResponse(
			responseCode = "200",
			description = "Successfully created",
			content = @Content(schema = @Schema(implementation = GroupSenderKeyResponse.class))
			)
	@ApiResponse(
			responseCode = "404",
			description = "Sender device not found",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
			)
	@Deprecated
	public ResponseEntity<CommonResponse<GroupSenderKeyResponse>> create(
			@Parameter(description = "Sender device ID", required = true)
			@PathVariable Integer senderDeviceId,

			@Parameter(description = "Group ID", required = true)
			@PathVariable UUID groupId,

			@Parameter(description = "SKDM upload request payload", required = true)
			@RequestBody GroupSenderKeyRequest request);

	/**
	 * Fetch SKDMs for a device + group
	 */
	@Operation(
			summary = "Fetch Sender Keys",
			description = "Retrieve SKDM records uploaded by a sender device for a specific group."
			)
	@ApiResponse(
			responseCode = "200",
			description = "Sender keys fetched",
			content = @Content(schema = @Schema(implementation = GroupSenderKeyResponse.class))
			)
	@ApiResponse(
			responseCode = "404",
			description = "Sender device not found",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
			)
	@Deprecated
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> get(
			@Parameter(description = "Sender device ID", required = true)
			@PathVariable Integer senderDeviceId,

			@Parameter(description = "Group ID", required = true)
			@PathVariable UUID groupId) ;

	/**
	 * Receiver device polls for SKDM (long polling)
	 */

	@Operation(
			summary = "Long-poll for new Sender Keys (SKDM)",
			description = """
					    Receiver device polls the server for any Sender Key Distribution Messages (SKDM)
					    intended for (receiverUserKey, receiverDeviceId, groupId).

					    The request will wait (long-poll) for up to timeoutMs milliseconds 
					    before returning an empty list if no new keys are available.
					"""
			)
	@ApiResponse(
			responseCode = "200",
			description = "Sender keys retrieved",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = GroupSenderKeyResponse.class)
					)
			)
	@ApiResponse(
			responseCode = "404",
			description = "No sender key records found for this user/device and group",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
			)
	@Deprecated
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> poll(

			@Parameter(
					description = "Receiver device ID requesting SKDM",
					required = true,
					example = "2"
					)
			@PathVariable Integer receiverDeviceId,

			@Parameter(
					description = "Group ID where the SKDM belongs",
					required = true,
					example = "group-1234"
					)
			@PathVariable UUID groupId,

			@Parameter(
					description = "Maximum wait time in milliseconds for long polling",
					example = "3000",
					required = false
					)
			@RequestParam(defaultValue = "3000") long timeoutMs
			);


	@Operation(
			summary = "Fetch Sender Keys (SKDM) for a group",
			description = """
					    Retrieves all Sender Key Distribution Messages (SKDM) for the specified receiver device
					    within a given group.

					    This API returns all pending sender keys that have not yet been consumed by the client.
					    Once the keys are fetched successfully, the client is expected to acknowledge consumption
					    using the ACK endpoint.
					"""
			)

	@ApiResponse(
			responseCode = "200",
			description = "Sender keys retrieved successfully",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = GroupSenderKeyResponse.class)
					)
			)

	@ApiResponse(
			responseCode = "404",
			description = "No sender key records found for this user/device and group",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = CommonResponse.class)
					)
			)

	@ApiResponse(
			responseCode = "500",
			description = "Internal server error",
			content = @Content(
					mediaType = "application/json",
					schema = @Schema(implementation = CommonResponse.class)
					)
			)
	@Deprecated
	public ResponseEntity<CommonResponse<List<GroupSenderKeyResponse>>> getSenderKeys(

			@Parameter(
					description = "Receiver device ID requesting SKDM",
					required = true,
					example = "2"
					)
			@PathVariable Integer receiverDeviceId,

			@Parameter(
					description = "Group ID where the SKDM belongs",
					required = true,
					example = "group-1234"
					)
			@PathVariable UUID groupId
			);  
}



