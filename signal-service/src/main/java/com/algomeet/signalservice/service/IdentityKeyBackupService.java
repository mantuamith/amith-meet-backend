package com.algomeet.signalservice.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.signalservice.dto.IdentityKeyBackupRequest;
import com.algomeet.signalservice.dto.IdentityKeyBackupResponse;
import com.algomeet.signalservice.dto.IdentityKeyBackupUpdateRequest;
import com.algomeet.signalservice.entity.IdentityKeyBackup;
import com.algomeet.signalservice.entity.IdentityKeyBackupId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.IdentityKeyBackupRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class IdentityKeyBackupService {
    private final IdentityKeyBackupRepository repository;

    @Transactional
    public IdentityKeyBackupResponse saveBackup(UUID userKey, IdentityKeyBackupRequest request) {    	
        IdentityKeyBackup backup = new IdentityKeyBackup();        
        backup.setId(new IdentityKeyBackupId(userKey, request.getDeviceId()));
        
        backup.setSerializedIdentityKey(request.getSerializedIdentityKey());
        backup.setSerializedPreKeys(request.getSerializedPreKeys());
        backup.setSerializedSignedPreKey(request.getSerializedSignedPreKey());
        backup.setSerializedKyberPreKey(request.getSerializedKyberPreKey());
        
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        IdentityKeyBackup saved = repository.save(backup);
        return toResponse(saved);
    }
    
    @Transactional
    public IdentityKeyBackupResponse updateBackup(UUID userKey, IdentityKeyBackupUpdateRequest request) {
    	Optional<IdentityKeyBackup> backupOpt = repository.findById(new IdentityKeyBackupId(userKey, request.getDeviceId()));
    	if (backupOpt.isEmpty()) {
    		throw new RecordNotFoundException(String.format("User device identity key backup not found for user ID %s, and device Id %s ", 
    				userKey, request.getDeviceId()));
    	}
    	
        IdentityKeyBackup backup = backupOpt.get();
        backup.setId(new IdentityKeyBackupId(userKey, request.getDeviceId()));
        
        if (StringUtils.hasLength(request.getSerializedIdentityKey())) {
        	backup.setSerializedIdentityKey(request.getSerializedIdentityKey());
        }
        
        if (!CollectionUtils.isEmpty(request.getSerializedPreKeys())) {
        	backup.setSerializedPreKeys(request.getSerializedPreKeys());
        }
        
        if (StringUtils.hasLength(request.getSerializedSignedPreKey())) {
        	backup.setSerializedSignedPreKey(request.getSerializedSignedPreKey());
        }
        
        if (StringUtils.hasLength(request.getSerializedKyberPreKey())) {
        	backup.setSerializedKyberPreKey(request.getSerializedKyberPreKey());
        }
        
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        backup.setUpdatedAt(Instant.now());
        IdentityKeyBackup saved = repository.save(backup);
        return toResponse(saved);
    }

    public Optional<IdentityKeyBackupResponse> restoreBackup(UUID userKey, Integer deviceId) {
        return repository.findById(new IdentityKeyBackupId(userKey, deviceId))
                .map(entity -> toResponse(entity));
    }
    
    public void deleteBackup(UUID userKey, Integer deviceId) {
    	if(repository.findById(new IdentityKeyBackupId(userKey, deviceId)).isEmpty()) {
    		throw new RecordNotFoundException(String.format("User device identity key backup for device Id %s is not found", deviceId));
    	}
    	
        repository.findById(new IdentityKeyBackupId(userKey, deviceId)).ifPresent(repository::delete);
    }
    
    public void deleteBackup(UUID userKey) {    	
        repository.deleteByIdUserKey(userKey);
    }
        
    public IdentityKeyBackupResponse toResponse(IdentityKeyBackup entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        return IdentityKeyBackupResponse.builder()
                .userKey(entity.getId().getUserKey())
                .deviceId(entity.getId().getDeviceId())
                .serializedIdentityKey(entity.getSerializedIdentityKey())
                .serializedPreKeys(entity.getSerializedPreKeys())
                .serializedSignedPreKey(entity.getSerializedSignedPreKey())
                .serializedKyberPreKey(entity.getSerializedKyberPreKey())
                .aesAlg(entity.getAesAlg())
                .version(entity.getVersion())
                .salt(entity.getSalt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
