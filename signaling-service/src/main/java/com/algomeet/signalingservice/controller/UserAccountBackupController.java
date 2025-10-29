package com.algomeet.signalingservice.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
public class UserAccountBackupController {
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
     * Retrieves the existing backup of the user's Matrix OLM device account.
     * <p>
     * If no backup is found for the user, a {@code 404 NOT FOUND} response is returned.
     *
     * @return a {@code 200 OK} response containing the backup data,
     *         or {@code 404 NOT FOUND} if no backup exists
     */
    @GetMapping
    public ResponseEntity<CommonResponse<UserAccountBackupResponse>> getBackup() {
        return service.restoreBackup(UUID.fromString(SecurityUtil.getUserKey()))
                .map(account -> ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, account)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                		.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND)));
    }

    /**
     * Deletes the existing backup of the user's Matrix OLM device account.
     * <p>
     * If the backup does not exist, a {@code 404 NOT FOUND} response is returned.
     *
     * @return a {@code 200 OK} response if the backup was successfully deleted,
     *         or {@code 404 NOT FOUND} if the backup does not exist
     */
    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> deleteBackup() {
    	try {
    		service.deleteBackup(UUID.fromString(SecurityUtil.getUserKey()));
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND)
    				.body(CommonResponse.from(ResponseCode.USER_ACCOUNT_BACKUP_NOT_FOUND));
    	}
    }
}