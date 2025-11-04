
package com.algomeet.signalingservice.controller.swagger;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserSessionBackupRequest;
import com.algomeet.signalingservice.dto.UserSessionBackupResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Session Backup", description = "Endpoints for saving, restoring, and deleting encrypted Matrix session backups (OLM).")
public interface UserSessionBackupControllerDoc {

	@Operation(
		summary = "Save or update user session backup",
		description = "Creates or updates a user session backup for the currently authenticated user. " +
		              "Each backup is tied to a specific session and device.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Session backup saved successfully",
					content = @Content(schema = @Schema(implementation = UserSessionBackupResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
		}
	)
	public ResponseEntity<CommonResponse<UserSessionBackupResponse>> saveBackup(
			@Validated @RequestBody UserSessionBackupRequest request);

	@Operation(
		summary = "Restore a specific session backup",
		description = "Retrieves a single encrypted session backup for the authenticated user by its session ID.",
		parameters = {
			@Parameter(name = "sessionId", description = "The unique identifier of the session to restore", required = true)
		},
		responses = {
			@ApiResponse(responseCode = "200", description = "Session restored successfully",
					content = @Content(schema = @Schema(implementation = UserSessionBackupResponse.class))),
			@ApiResponse(responseCode = "404", description = "Session backup not found", content = @Content)
		}
	)
	public ResponseEntity<CommonResponse<UserSessionBackupResponse>> restoreSession(
			@PathVariable String sessionId);

	@Operation(
		summary = "Restore all session backups for a device",
		description = "Retrieves all session backups associated with a specific device ID. " +
		              "The device must belong to the currently authenticated user.",
		parameters = {
			@Parameter(name = "deviceId", description = "The device ID linked to the session backups", required = true)
		},
		responses = {
			@ApiResponse(responseCode = "200", description = "Sessions restored successfully",
					content = @Content(schema = @Schema(implementation = UserSessionBackupResponse.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
		}
	)
	public ResponseEntity<CommonResponse<List<UserSessionBackupResponse>>> restoreSessions(
			@RequestParam("deviceId") String deviceId);

	@Operation(
		summary = "Delete a specific session backup",
		description = "Deletes an existing session backup for the currently authenticated user by session ID.",
		parameters = {
			@Parameter(name = "sessionId", description = "The unique identifier of the session to delete", required = true)
		},
		responses = {
			@ApiResponse(responseCode = "200", description = "Session deleted successfully", content = @Content),
			@ApiResponse(responseCode = "404", description = "Session not found", content = @Content)
		}
	)
	public ResponseEntity<CommonResponse<?>> deleteSession(@PathVariable String sessionId);

	@Operation(
		summary = "Delete all user session backups",
		description = "Deletes all session backups associated with the currently authenticated user.",
		responses = {
			@ApiResponse(responseCode = "200", description = "All session backups deleted successfully", content = @Content),
			@ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
		}
	)
	public ResponseEntity<CommonResponse<?>> deleteSessions();
}
