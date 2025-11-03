package com.algomeet.signalingservice.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.dto.UserAccountBackupRequest;
import com.algomeet.signalingservice.dto.UserAccountBackupResponse;
import com.algomeet.signalingservice.entity.UserAccountBackup;
import com.algomeet.signalingservice.entity.UserAccountBackupId;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.repository.UserAccountBackupRepository;

@Service
public class UserAccountBackupService {
    private final UserAccountBackupRepository repository;

    public UserAccountBackupService(UserAccountBackupRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserAccountBackupResponse saveBackup(UUID userKey, UserAccountBackupRequest request) {    	
        UserAccountBackup backup = new UserAccountBackup();
        backup.setEncryptedAccount(request.getEncryptedAccount());
        backup.setId(new UserAccountBackupId(userKey, request.getDeviceId()));
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        if (backup.getCreatedAt() == null) {
        	backup.setCreatedAt(Instant.now());
        }
        backup.setUpdatedAt(Instant.now());
        UserAccountBackup saved = repository.save(backup);
        return toResponse(saved);
    }
    
    @Transactional
    public UserAccountBackupResponse updateBackup(UUID userKey, UserAccountBackupRequest request) {
    	Optional<UserAccountBackup> backupOpt = repository.findById(new UserAccountBackupId(userKey, request.getDeviceId()));
    	if (backupOpt.isEmpty()) {
    		throw new RecordNotFoundException(String.format("User account backup not found for user ID %s, and device Id %s ", 
    				userKey, request.getDeviceId()));
    	}
    	
        UserAccountBackup backup = backupOpt.get();
        backup.setEncryptedAccount(request.getEncryptedAccount());
        backup.setId(new UserAccountBackupId(userKey, request.getDeviceId()));
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        if (backup.getCreatedAt() == null) {
        	backup.setCreatedAt(Instant.now());
        }
        backup.setUpdatedAt(Instant.now());
        UserAccountBackup saved = repository.save(backup);
        return toResponse(saved);
    }

    public Optional<UserAccountBackupResponse> restoreBackup(UUID userKey, String deviceId) {
        return repository.findById(new UserAccountBackupId(userKey, deviceId))
                .map(entity -> toResponse(entity));
    }
    
    public void deleteBackup(UUID userKey, String deviceId) {
    	if(repository.findById(new UserAccountBackupId(userKey, deviceId)).isEmpty()) {
    		throw new RecordNotFoundException(String.format("User account backup for device Id %s is not found", deviceId));
    	}
    	
        repository.findById(new UserAccountBackupId(userKey, deviceId)).ifPresent(repository::delete);
    }
    
    public void deleteBackup(UUID userKey) {    	
        repository.deleteByIdUserKey(userKey);
    }
    
    
    public UserAccountBackupResponse toResponse(UserAccountBackup entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        return UserAccountBackupResponse.builder()
                .userKey(entity.getId().getUserKey())
                .deviceId(entity.getId().getDeviceId())
                .encryptedAccount(entity.getEncryptedAccount())
                .aesAlg(entity.getAesAlg())
                .version(entity.getVersion())
                .salt(entity.getSalt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
