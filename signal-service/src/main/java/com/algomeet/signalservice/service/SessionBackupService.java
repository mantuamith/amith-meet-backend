package com.algomeet.signalservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.SessionBackupRequest;
import com.algomeet.signalservice.dto.SessionBackupResponse;
import com.algomeet.signalservice.entity.SessionBackup;
import com.algomeet.signalservice.entity.SessionBackupId;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.SessionBackupRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class SessionBackupService {
    private final SessionBackupRepository repository;
    private final UserDeviceRepository deviceRepository;

    @Transactional
    public SessionBackupResponse saveBackup(UUID userKey, Integer deviceId, SessionBackupRequest request) { 
		if (deviceRepository.findById(new UserDeviceId(userKey, deviceId)).isEmpty()) {
			throw new RecordNotFoundException("User device ID not found");
		}
		
        SessionBackup backup = new SessionBackup();
        backup.setId(new SessionBackupId(userKey, 
        		deviceId,
        		request.getRegistrationId(), 
        		request.getRemoteUserKey(), 
        		request.getRemoteDeviceId()));
        
        backup.setSerializedSession(request.getSerializedSession());
        backup.setInbound(request.isInbound());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());

        SessionBackup saved = repository.save(backup);        
        return toResponse(saved);
    }
    
    public List<SessionBackupResponse> restoreSessions(UUID userkey, Integer deviceId) {
    	return repository.findByIdUserKeyAndIdDeviceId(userkey, deviceId).stream()
    			.map(entity -> {
    				return toResponse(entity);
    			})
    			.toList();
    }

    public void deleteByDeviceRegistrationAndRemoteUser(UUID userkey, Integer deviceId, Integer registrationId, UUID remoteUserKey, Integer remoteDeviceId) {
    	Optional<SessionBackup> sessionOpt = repository.findById(new SessionBackupId(userkey, deviceId, registrationId, remoteUserKey, remoteDeviceId));

    	if (sessionOpt.isPresent()) {
    		sessionOpt.ifPresent(repository::delete);
    	} else {
    		throw new RecordNotFoundException(String.format("User session not found %s, %s, %d", userkey, deviceId, registrationId));
    	}
    }
    
    public void deleteByDeviceId(UUID userKey, Integer deviceId) {
    	repository.deleteByIdUserKeyAndIdDeviceId(userKey, deviceId);
    }
        
    private SessionBackupResponse toResponse(SessionBackup entity) {
        if (entity == null) {
            return null;
        }

        return SessionBackupResponse.builder()
                .userKey(entity.getId().getUserKey())
                .deviceId(entity.getId().getDeviceId())
                .registrationId(entity.getId().getRegistrationId())
                .remoteUserKey(entity.getId().getRemoteUserKey())
                .remoteDeviceId(entity.getId().getRemoteDeviceId())
                .serializedSession(entity.getSerializedSession())
                .inbound(entity.isInbound())

                .aesAlg(entity.getAesAlg())
                .salt(entity.getSalt())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}