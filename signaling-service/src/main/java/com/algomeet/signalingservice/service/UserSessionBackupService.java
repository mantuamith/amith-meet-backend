package com.algomeet.signalingservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.dto.UserSessionBackupRequest;
import com.algomeet.signalingservice.dto.UserSessionBackupResponse;
import com.algomeet.signalingservice.entity.UserSessionBackup;
import com.algomeet.signalingservice.entity.UserSessionBackupId;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.repository.UserSessionBackupRepository;

@Service
public class UserSessionBackupService {
    private final UserSessionBackupRepository repository;
 
    public UserSessionBackupService(UserSessionBackupRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserSessionBackupResponse saveBackup(UUID userKey, UserSessionBackupRequest request) { 
        UserSessionBackup backup = new UserSessionBackup();
        backup.setUserKey(userKey);
        backup.setSessionId(request.getSessionId());
        backup.setPeerUserKey(request.getPeerUserKey());
        backup.setDeviceId(request.getDeviceId());
        backup.setInbound(request.isInbound());
        backup.setEncryptedSession(request.getEncryptedSession());
        backup.setAlgorithm(request.getAlgorithm());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());
        backup.setCreatedAt(Instant.now());

        UserSessionBackup saved = repository.save(backup);        
        return UserSessionBackupResponse.builder()
				.userKey(saved.getUserKey())
				.sessionId(saved.getSessionId())
				.peerUserKey(saved.getPeerUserKey())
				.deviceId(saved.getDeviceId())
				.encryptedSession(saved.getEncryptedSession())
				.inbound(saved.isInbound())    						
				.algorithm(saved.getAlgorithm())
				.aesAlg(saved.getAesAlg())
				.salt(saved.getSalt())
				.version(saved.getVersion())
        		.createdAt(saved.getCreatedAt())
        		.updatedAt(saved.getUpdatedAt())
				.build();
    }

    public Optional<UserSessionBackupResponse> restoreSession(UUID userkey, String sessionId) {
    	return repository.findById(new UserSessionBackupId(userkey, sessionId))
    			.map(entity -> {
    				UserSessionBackupResponse dto = UserSessionBackupResponse.builder()
    						.userKey(entity.getUserKey())
    						.sessionId(entity.getSessionId())
    						.peerUserKey(entity.getPeerUserKey())
    						.deviceId(entity.getDeviceId())
    						.encryptedSession(entity.getEncryptedSession())
    						.inbound(entity.isInbound())    						
    						.algorithm(entity.getAlgorithm())
    						.aesAlg(entity.getAesAlg())
    						.salt(entity.getSalt())
    						.version(entity.getVersion())
    		        		.createdAt(entity.getCreatedAt())
    		        		.updatedAt(entity.getUpdatedAt())
    						.build();
    				return dto;
    			});
    }
    
    public List<UserSessionBackupResponse> restoreSessions(UUID userkey, String deviceId) {
    	return repository.findByUserKeyAndDeviceId(userkey, deviceId).stream()
    			.map(entity -> {
    				UserSessionBackupResponse dto = UserSessionBackupResponse.builder()
    						.userKey(entity.getUserKey())
    						.sessionId(entity.getSessionId())
    						.peerUserKey(entity.getPeerUserKey())
    						.deviceId(entity.getDeviceId())
    						.encryptedSession(entity.getEncryptedSession())
    						.inbound(entity.isInbound())    						
    						.algorithm(entity.getAlgorithm())
    						.aesAlg(entity.getAesAlg())
    						.salt(entity.getSalt())
    						.version(entity.getVersion())
    		        		.createdAt(entity.getCreatedAt())
    		        		.updatedAt(entity.getUpdatedAt())
    						.build();
    				return dto;
    			})
    			.toList();
    }

    public void deleteBySessionId(UUID userKey, String sessionId) {
    	Optional<UserSessionBackup> sessionOpt = repository.findById(new UserSessionBackupId(userKey, sessionId));

    	if (sessionOpt.isPresent()) {
    		sessionOpt.ifPresent(repository::delete);
    	} else {
    		throw new RecordNotFoundException(String.format("Inbound group session not found %s, %s, %d", userKey, sessionId));
    	}
    }
}