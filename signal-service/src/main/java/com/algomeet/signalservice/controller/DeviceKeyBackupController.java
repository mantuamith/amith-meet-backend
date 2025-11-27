package com.algomeet.signalservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.DeviceKeyBackupControllerDoc;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupRequest;
import com.algomeet.signalservice.dto.DeviceKeyBackupResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupUpdateRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.DeviceKeyBackupService;
import com.algomeet.signalservice.util.SecurityUtil;

import lombok.AllArgsConstructor;


/**
 * REST controller responsible for handling operations related to
 * backing up and restoring a user's Signal device identity key.
 * <p>
 * Provides endpoints for saving, updating, retrieving, and deleting
 * user account backup data securely.
 */
@AllArgsConstructor
@RestController
@RequestMapping("/signal/backup/device-keys")
public class DeviceKeyBackupController implements DeviceKeyBackupControllerDoc{
	private final DeviceKeyBackupService service;


	/**
	 * Saves a new backup of the user's Signal device identity key.
	 * <p>
	 *
	 * @param request the backup data to be stored
	 * @return a {@code 200 OK} response containing the saved backup details
	 *         or {@code 302 FOUND} if the backup already exists
	 */
	@Override
	@PostMapping
	public ResponseEntity<CommonResponse<DeviceKeyBackupResponse>> saveBackup(@Validated @RequestBody DeviceKeyBackupRequest request) {
		DeviceKeyBackupResponse saved = service.saveBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));     
	}
	
	/**
	 * Update a new backup of the user's Signal device identity key.
	 * <p>
	 *
	 * @param request the backup data to be stored
	 * @return a {@code 200 OK} response containing the saved backup details
	 *         or {@code 302 FOUND} if the backup already exists
	 */
	@Override
	@PutMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<DeviceKeyBackupResponse>> updateBackup(@PathVariable("deviceId") Integer deviceId, 
			@Validated @RequestBody DeviceKeyBackupUpdateRequest request) {
		DeviceKeyBackupResponse saved = service.updateBackup(UUID.fromString(SecurityUtil.getUserKey()), deviceId, request);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));     
	}

	/**
	 * Retrieves a user device identity key backup for the authenticated user by device ID.
	 *
	 * <p>This endpoint allows clients to fetch a previously saved encrypted
	 * account backup associated with a specific device. If no backup is found,
	 * a {@code 404 NOT FOUND} response is returned.</p>
	 *
	 * @param deviceId the device identifier used to locate the user's account backup
	 * @return a {@link ResponseEntity} containing:
	 *         <ul>
	 *           <li>{@link HttpStatus#OK} with the {@link DeviceKeyBackupResponse} if found</li>
	 *           <li>{@link HttpStatus#NOT_FOUND} if no backup exists for the given device ID</li>
	 *         </ul>
	 */
	@Override
	@GetMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<DeviceKeyBackupResponse>> getBackup(@PathVariable("deviceId") Integer deviceId) {
		return service.restoreBackup(UUID.fromString(SecurityUtil.getUserKey()), deviceId)
				.map(account -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, account)))
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(CommonResponse.from(ResponseCode.DEVICE_KEY_BACKUP_NOT_FOUND)));
	}

	/**
	 * Deletes a specific user device identity key backup associated with the authenticated user and device ID.
	 *
	 * <p>This endpoint allows a user to permanently delete their encrypted account backup
	 * for a specific device. If the specified backup is not found, a {@code 404 NOT FOUND}
	 * response is returned.</p>
	 *
	 * @param deviceId the device identifier whose backup should be deleted
	 * @return a {@link ResponseEntity} containing:
	 *         <ul>
	 *           <li>{@link HttpStatus#OK} if the backup was successfully deleted</li>
	 *           <li>{@link HttpStatus#NOT_FOUND} if no backup exists for the given device ID</li>
	 *         </ul>
	 */
	@Override
	@DeleteMapping("/{deviceId}")
	public ResponseEntity<CommonResponse<?>> deleteBackup(@PathVariable("deviceId") Integer deviceId) {
		try {
			service.deleteBackup(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.DEVICE_KEY_BACKUP_NOT_FOUND));
		}
	}

	/**
	 * Deletes all user device identity key backups associated with the authenticated user.
	 *
	 * <p>This endpoint removes all stored encrypted account backups for the current user,
	 * regardless of device. Once deleted, the backups cannot be recovered.</p>
	 *
	 * @return a {@link ResponseEntity} with {@link HttpStatus#OK} upon successful deletion
	 */
	@Override
	@DeleteMapping("/all")
	public ResponseEntity<CommonResponse<?>> deleteAllUserBackup() {
		service.deleteBackup(UUID.fromString(SecurityUtil.getUserKey()));
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

}