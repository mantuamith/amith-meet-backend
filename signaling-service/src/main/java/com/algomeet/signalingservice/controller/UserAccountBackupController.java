package com.algomeet.signalingservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalingservice.controller.swagger.UserAccountBackupControllerDoc;
import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.UserAccountBackupRequest;
import com.algomeet.signalingservice.dto.UserAccountBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.exceptions.UserAccountBackupAlreadyExistsException;
import com.algomeet.signalingservice.service.UserAccountBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

/**
 * REST controller responsible for handling operations related to
 * backing up and restoring a user's Matrix OLM device account.
 * <p>
 * Provides endpoints for saving, updating, retrieving, and deleting
 * user account backup data securely.
 */
@RestController
@RequestMapping("/signaling/backup/user-account")
public class UserAccountBackupController implements UserAccountBackupControllerDoc{
    private final UserAccountBackupService service;

    public UserAccountBackupController(UserAccountBackupService service) {
        this.service = service;
    }


    /**
     * Saves a new backup of the user's Matrix OLM device account.
     * <p>
     * If a backup already exists for the current user, a {@code 302 FOUND} response
     * is returned indicating that the backup already exists.
     *
     * @param request the backup data to be stored
     * @return a {@code 200 OK} response containing the saved backup details
     *         or {@code 302 FOUND} if the backup already exists
     */
    @Override
    @PostMapping
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> saveBackup(@Validated @RequestBody UserAccountBackupRequest request) {
    	try {
    		UserAccountBackupResponse saved = service.saveBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
    	} catch(UserAccountBackupAlreadyExistsException ex) {
    		return ResponseEntity.status(HttpStatus.FOUND)
            		.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_ALREADY_EXIST));
    	}        
    }
    
    /**
     * Updates an existing backup of the user's Matrix OLM device account.
     * <p>
     * If no existing backup is found, a {@code 404 NOT FOUND} response is returned.
     *
     * @param request the updated backup data
     * @return a {@code 200 OK} response containing the updated backup details,
     *         or {@code 404 NOT FOUND} if the backup does not exist
     */
    @Override
    @PutMapping
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> updateBackup(@Validated @RequestBody UserAccountBackupRequest request) {
    	try {
    		UserAccountBackupResponse saved = service.updateBackup(UUID.fromString(SecurityUtil.getUserKey()), request);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
    	} catch(UserAccountBackupAlreadyExistsException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND)
    				.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND));
    	}        
    }

    /**
     * Retrieves a user account backup for the authenticated user by device ID.
     *
     * <p>This endpoint allows clients to fetch a previously saved encrypted
     * account backup associated with a specific device. If no backup is found,
     * a {@code 404 NOT FOUND} response is returned.</p>
     *
     * @param deviceId the device identifier used to locate the user's account backup
     * @return a {@link ResponseEntity} containing:
     *         <ul>
     *           <li>{@link HttpStatus#OK} with the {@link UserAccountBackupResponse} if found</li>
     *           <li>{@link HttpStatus#NOT_FOUND} if no backup exists for the given device ID</li>
     *         </ul>
     */
    @Override
    @GetMapping
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> getBackup(@RequestParam("deviceId") String deviceId) {
        return service.restoreBackup(UUID.fromString(SecurityUtil.getUserKey()), deviceId)
                .map(account -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, account)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND)));
    }

    /**
     * Deletes a specific user account backup associated with the authenticated user and device ID.
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
    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> deleteBackup(@RequestParam("deviceId") String deviceId) {
    	try {
    		service.deleteBackup(UUID.fromString(SecurityUtil.getUserKey()), deviceId);
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND)
    				.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND));
    	}
    }
    
    /**
     * Deletes all user account backups associated with the authenticated user.
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