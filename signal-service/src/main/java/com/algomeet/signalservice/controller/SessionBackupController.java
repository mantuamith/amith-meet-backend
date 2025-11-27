package com.algomeet.signalservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.SessionBackupControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.SessionBackupRequest;
import com.algomeet.signalservice.dto.SessionBackupResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.SessionBackupService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.AllArgsConstructor;

/**
 * REST controller that handles user session backup operations such as
 * saving, restoring, and deleting session backups.
 */
@AllArgsConstructor
@RestController
@RequestMapping("/signal/backup/devices/{deviceId}/sessions")
public class SessionBackupController implements SessionBackupControllerDoc{
	private final SessionBackupService service;


	/**
	 * Saves or updates a user session backup for the currently authenticated user.
	 *
	 * @param request the request payload containing session backup details
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} with the saved session data
	 */
	@Override
	@PostMapping
	public ResponseEntity<CommonResponse<SessionBackupResponse>> saveBackup(@PathVariable Integer deviceId, 
			@Validated @RequestBody SessionBackupRequest request) {
		try {
			SessionBackupResponse savedSession = service.saveBackup(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, savedSession));
		} catch(RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
					CommonResponse.from(ResponseCode.USER_DEVICE_ID_NOT_FOUND));
		}
	}

	/**
	 * Retrieves all encrypted user session backups associated with a specific device.
	 * <p>
	 * This endpoint allows a user to restore previously backed-up Signal user session data
	 * (e.g., Signal sessions) for a given device ID. The device ID must correspond
	 * to the user's device that originally created the backup.
	 *
	 * @param deviceId the unique identifier of the user's device for which session backups are being restored
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} object that holds:
	 *         <ul>
	 *             <li>{@link ResponseCode#SUCCESS} if the sessions were successfully retrieved</li>
	 *             <li>A list of {@link SessionBackupResponse} objects representing the restored sessions</li>
	 *         </ul>
	 * 
	 * @apiNote The request must be authenticated; the user's key is resolved from the current security context.
	 * 
	 * @see com.algomeet.signalingservice.dto.SessionBackupResponse
	 * @see com.algomeet.signalingservice.enums.ResponseCode
	 * @see com.algomeet.signalingservice.util.SecurityUtil
	 */
	@Override
	@GetMapping
	public ResponseEntity<CommonResponse<List<SessionBackupResponse>>> restoreSessions(@PathVariable Integer deviceId) {
		List<SessionBackupResponse> sessions = service.restoreSessions(UUID.fromString(SecurityUtil.getUserKey()), deviceId);

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, sessions));
	}

	/**
	 * Deletes sessions by device Id for the currently authenticated user.
	 *
	 * @param deviceId of the sessions to delete
	 * @return a {@link ResponseEntity} containing a {@link CommonResponse} indicating success or failure
	 */
	@Override
	@DeleteMapping
	public ResponseEntity<CommonResponse<?>> deleteSessions(
			@PathVariable Integer deviceId) {		
		try {
			service.deleteByDeviceId(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_SESSION_BACKUP_NOT_FOUND));
		}
	}

	@Override
	@DeleteMapping("/registration-and-remote-user")
	public ResponseEntity<CommonResponse<?>> deleteByDeviceRegistrationAndRemoteUser(@PathVariable Integer deviceId, 
			@RequestParam Integer registrationId, 
			@RequestParam UUID remoteUserKey, 
			@RequestParam Integer remoteDeviceId) {		

		try {
			service.deleteByDeviceRegistrationAndRemoteUser(UUID.fromString(SecurityUtil.getUserKey()), 
					deviceId,
					registrationId, 
					remoteUserKey, 
					remoteDeviceId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.USER_SESSION_BACKUP_NOT_FOUND));
		}
	}
}
