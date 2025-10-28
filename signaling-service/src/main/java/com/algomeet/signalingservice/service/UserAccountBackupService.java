package com.algomeet.signalingservice.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.dto.UserAccountBackupRequest;
import com.algomeet.signalingservice.dto.UserAccountBackupResponse;
import com.algomeet.signalingservice.entity.UserAccountBackup;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.exceptions.UserAccountBackupAlreadyExistsException;
import com.algomeet.signalingservice.repository.UserAccountBackupRepository;

@Service
public class UserAccountBackupService {
    private final UserAccountBackupRepository repository;

    public UserAccountBackupService(UserAccountBackupRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserAccountBackupResponse saveBackup(UUID userKey, UserAccountBackupRequest request) {
    	Optional<UserAccountBackup> backupOpt = repository.findById(userKey);
    	if (backupOpt.isPresent()) {
    		throw new UserAccountBackupAlreadyExistsException("User account backup already exist for user ID " + userKey);
    	}
    	
        UserAccountBackup backup = new UserAccountBackup();

        backup.setUserKey(userKey);
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        if (backup.getCreatedAt() == null) {
        	backup.setCreatedAt(Instant.now());
        }
        backup.setUpdatedAt(Instant.now());
        UserAccountBackup saved = repository.save(backup);
        return UserAccountBackupResponse.builder()
        		.userKey(saved.getUserKey())
        		.aesAlg(saved.getAesAlg())
        		.version(saved.getVersion())
        		.salt(saved.getSalt())
        		.createdAt(saved.getCreatedAt())
        		.updatedAt(saved.getUpdatedAt())
        		.build();
    }
    
    @Transactional
    public UserAccountBackupResponse updateBackup(UUID userKey, UserAccountBackupRequest request) {
    	Optional<UserAccountBackup> backupOpt = repository.findById(userKey);
    	if (backupOpt.isEmpty()) {
    		throw new RecordNotFoundException("User account backup not found for user ID " + userKey);
    	}
    	
        UserAccountBackup backup = backupOpt.get();

        backup.setUserKey(userKey);
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        if (backup.getCreatedAt() == null) {
        	backup.setCreatedAt(Instant.now());
        }
        backup.setUpdatedAt(Instant.now());
        UserAccountBackup saved = repository.save(backup);
        return UserAccountBackupResponse.builder()
        		.userKey(saved.getUserKey())
        		.aesAlg(saved.getAesAlg())
        		.version(saved.getVersion())
        		.salt(saved.getSalt())
        		.createdAt(saved.getCreatedAt())
        		.updatedAt(saved.getUpdatedAt())
        		.build();
    }

    public Optional<UserAccountBackupResponse> restoreBackup(UUID userKey) {
        return repository.findById(userKey)
                .map(entity -> UserAccountBackupResponse.builder()
                        .userKey(entity.getUserKey())
                        .aesAlg(entity.getAesAlg())
                        .version(entity.getVersion())
                        .salt(entity.getSalt())
                        .createdAt(entity.getCreatedAt())
                        .updatedAt(entity.getUpdatedAt())
                        .build());
    }

    public void deleteBackup(UUID userKey) {
    	if(repository.findById(userKey).isEmpty()) {
    		throw new RecordNotFoundException("User account backup not found");
    	}
    	
        repository.findById(userKey).ifPresent(repository::delete);
    }
}
