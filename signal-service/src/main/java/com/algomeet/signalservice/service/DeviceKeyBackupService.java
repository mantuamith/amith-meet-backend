package com.algomeet.signalservice.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.signalservice.dto.DeviceKeyBackupRequest;
import com.algomeet.signalservice.dto.DeviceKeyBackupResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupUpdateRequest;
import com.algomeet.signalservice.entity.DeviceKeyBackup;
import com.algomeet.signalservice.entity.DeviceKeyBackupId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.DeviceKeyBackupRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class DeviceKeyBackupService {
    private final DeviceKeyBackupRepository repository;

    @Transactional
    public DeviceKeyBackupResponse saveBackup(UUID userKey, DeviceKeyBackupRequest request) {    	
        DeviceKeyBackup backup = new DeviceKeyBackup();        
        backup.setId(new DeviceKeyBackupId(userKey, request.getDeviceId()));
        
        backup.setSerializedIdentityKey(request.getSerializedIdentityKey());
        backup.setSerializedPreKeys(request.getSerializedPreKeys());
        backup.setSerializedSignedPreKey(request.getSerializedSignedPreKey());
        backup.setSerializedKyberPreKey(request.getSerializedKyberPreKey());
        
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        DeviceKeyBackup saved = repository.save(backup);
        return toResponse(saved);
    }
    
    @Transactional
    public DeviceKeyBackupResponse updateBackup(UUID userKey, DeviceKeyBackupUpdateRequest request) {
    	Optional<DeviceKeyBackup> backupOpt = repository.findById(new DeviceKeyBackupId(userKey, request.getDeviceId()));
    	if (backupOpt.isEmpty()) {
    		throw new RecordNotFoundException(String.format("User device identity key backup not found for user ID %s, and device Id %s ", 
    				userKey, request.getDeviceId()));
    	}
    	
        DeviceKeyBackup backup = backupOpt.get();
        backup.setId(new DeviceKeyBackupId(userKey, request.getDeviceId()));
        
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
        DeviceKeyBackup saved = repository.save(backup);
        return toResponse(saved);
    }

    public Optional<DeviceKeyBackupResponse> restoreBackup(UUID userKey, Integer deviceId) {
        return repository.findById(new DeviceKeyBackupId(userKey, deviceId))
                .map(entity -> toResponse(entity));
    }
    
    public void deleteBackup(UUID userKey, Integer deviceId) {
    	if(repository.findById(new DeviceKeyBackupId(userKey, deviceId)).isEmpty()) {
    		throw new RecordNotFoundException(String.format("User device identity key backup for device Id %s is not found", deviceId));
    	}
    	
        repository.findById(new DeviceKeyBackupId(userKey, deviceId)).ifPresent(repository::delete);
    }
    
    public void deleteBackup(UUID userKey) {    	
        repository.deleteByIdUserKey(userKey);
    }
        
    public DeviceKeyBackupResponse toResponse(DeviceKeyBackup entity) {
        if (entity == null || entity.getId() == null) {
            return null;
        }

        return DeviceKeyBackupResponse.builder()
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
