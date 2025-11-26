package com.algomeet.signalservice.controller.swagger;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.SessionBackupRequest;
import com.algomeet.signalservice.dto.SessionBackupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Session Backup", description = "APIs for storing, restoring, and deleting Signal protocol session backups")
public interface SessionBackupControllerDoc {
	// ---------------------------------------------------------
	// POST /signal/backup/sessions
	// ---------------------------------------------------------

	@Operation(
		summary = "Save or update a Signal session backup",
		description = """
			Stores a new encrypted session backup or updates an existing one for the
			current authenticated user. The backup may contain inbound and outbound
			sessions, ratchet states, and related Signal session metadata.
			""",
		tags = { "Session Backup" }
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Session backup saved successfully",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "400",
			description = "Invalid request payload",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "401",
			description = "Unauthorized",
			content = @Content
		)
	})
	public ResponseEntity<CommonResponse<SessionBackupResponse>> saveBackup(
			@Validated
			@io.swagger.v3.oas.annotations.parameters.RequestBody(
				description = "Encrypted session backup payload",
				required = true
			)
			@RequestBody SessionBackupRequest request);


	// ---------------------------------------------------------
	// GET /signal/backup/sessions/{deviceId}
	// ---------------------------------------------------------

	@Operation(
		summary = "Restore all session backups by device",
		description = """
			Retrieves all session backups associated with a specific device ID belonging
			to the authenticated user. The returned data includes encrypted Signal
			session records ready to be restored into the libsignal SessionStore.
			""",
		tags = { "Session Backup" }
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Session backups retrieved successfully",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "401",
			description = "Unauthorized",
			content = @Content
		),
		@ApiResponse(
			responseCode = "404",
			description = "No backups found for the specified device",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		)
	})
	public ResponseEntity<CommonResponse<List<SessionBackupResponse>>> restoreSessions(
			@Parameter(description = "The user's local device ID", required = true)
			@PathVariable Integer deviceId);


	// ---------------------------------------------------------
	// DELETE /signal/backup/sessions/{deviceId}
	// ---------------------------------------------------------

	@Operation(
		summary = "Delete all session backups for a device",
		description = """
			Deletes all session backups created by a specific device ID for the
			current authenticated user.
			""",
		tags = { "Session Backup" }
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Session backups deleted successfully",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "No session backups found for the device",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		)
	})
	@DeleteMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<?>> deleteSessions(
			@Parameter(description = "The device ID whose sessions will be deleted", required = true)
			@PathVariable Integer deviceId);


	// ---------------------------------------------------------
	// DELETE /signal/backup/sessions/{deviceId}?registrationId&remoteUserKey&remoteDeviceId
	// ---------------------------------------------------------

	@Operation(
		summary = "Delete a specific session backup record",
		description = """
			Deletes a session backup based on local device ID, remote registration ID,
			remote user's key, and remote device ID.
			
			Useful for deleting a single peer-to-peer Signal session.
			""",
		tags = { "Session Backup" }
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Specific session backup deleted successfully",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		),
		@ApiResponse(
			responseCode = "404",
			description = "Matching session backup not found",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		)
	})
	public ResponseEntity<CommonResponse<?>> deleteByDeviceRegistrationAndRemoteUser(
			@Parameter(description = "Local device ID", required = true)
			@PathVariable Integer deviceId,

			@Parameter(description = "Local registrationId used during session creation", required = true)
			@RequestParam Integer registrationId,

			@Parameter(description = "Remote user's UUID", required = true)
			@RequestParam UUID remoteUserKey,

			@Parameter(description = "Remote device ID", required = true)
			@RequestParam Integer remoteDeviceId);


	// ---------------------------------------------------------
	// DELETE /signal/backup/sessions/by-user-key
	// ---------------------------------------------------------

	@Operation(
		summary = "Delete all session backups for the authenticated user",
		description = """
			Deletes every session backup associated with the authenticated user's UUID.
			Useful when resetting or reinstalling all Signal devices.
			""",
		tags = { "Session Backup" }
	)
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "All user session backups deleted",
			content = @Content(schema = @Schema(implementation = CommonResponse.class))
		)
	})
	public ResponseEntity<CommonResponse<?>> deleteByUserkeySessions();
}
