package com.algomeet.signalingservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalingservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalingservice.entity.InboundGroupSessionBackup;
import com.algomeet.signalingservice.entity.InboundGroupSessionBackupId;
import com.algomeet.signalingservice.entity.OutboundGroupSessionBackup;
import com.algomeet.signalingservice.entity.OutboundGroupSessionBackupId;
import com.algomeet.signalingservice.exceptions.MaxSessionsLimitExceededException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.repository.InboundGroupSessionBackupRepository;
import com.algomeet.signalingservice.repository.OutboundGroupSessionBackupRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@Service
@Data
public class GroupSessionBackupService {
	@Value("${group-session.max-inbound-sessions-limit:15000}")
	private int maxInboundSessionsLimit;
	
	@Value("${group-session.max-outbound-sessions-limit:7000}")
	private int maxOutboundSessionsLimit;
	
	@Autowired
    private InboundGroupSessionBackupRepository inboundRepo;
	
	@Autowired
    private OutboundGroupSessionBackupRepository outboundRepo;

    // -------------------------
    // Inbound backup methods
    // -------------------------

    @Transactional
    public GroupSessionBackupResponse saveInboundBackup(UUID userKey, GroupSessionBackupRequest request) {
    	if (inboundRepo.countById_UserKey(userKey) > maxInboundSessionsLimit) {
    		throw new MaxSessionsLimitExceededException("Maximum inbound sessions limit exceeded");
    	}
    	
        InboundGroupSessionBackup backup = new InboundGroupSessionBackup();
        
        int ratchetIndex = request.getRatchetIndex() == null ? 0 : request.getRatchetIndex();
        backup.setId(new InboundGroupSessionBackupId(userKey, request.getSessionId(), ratchetIndex));

        backup.setPeerUserKey(request.getPeerUserKey());
        backup.setGroupId(request.getGroupId());
        backup.setEncryptedSession(request.getEncryptedSession());
        backup.setAlgorithm(request.getAlgorithm());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());
        backup.setCreatedAt(Instant.now());

        InboundGroupSessionBackup saved = inboundRepo.save(backup);
        return toResponse(saved);
    }

    /**
     * Restore inbound session by sessionId (for resuming encryption).
     */
    public Optional<GroupSessionBackupResponse> restoreInboundSession(UUID userKey, Integer ratchetIndex, String sessionId) {
        return inboundRepo.findClosestBackup(userKey, sessionId, ratchetIndex)
                .map(entity -> {
                	return toResponse(entity);
                });
    }
    
    /**
     * Restore all inbound sessions by sessionId (for resuming encryption).
     */
    public List<GroupSessionBackupResponse> restoreInboundSessions(UUID userKey) {
        return inboundRepo.findHighestRatchetIndexByUserKeyGroupedBySessionId(userKey)
        		.stream()
                .map(entity -> {
                	return toResponse(entity);
                })
                .toList();
    }

    @Transactional
    public void pruneInboundBackups(UUID userKey, String sessionId, int keepLastN) {
        List<InboundGroupSessionBackup> backups = inboundRepo.findById_UserKey(userKey)
                .stream()
                .filter(b -> b.getId().getSessionId().equals(sessionId))
                .sorted((a, b) -> b.getId().getRatchetIndex() - a.getId().getRatchetIndex())
                .toList();

        if (backups.size() > keepLastN) {
            backups.subList(keepLastN, backups.size()).forEach(inboundRepo::delete);
        }
    }
    
    /**
     * Delete stale inbound sessions (e.g., rotated or expired).
     */
    @Transactional
    public void deleteInboundSession(UUID userKey, String sessionId, int ratchetIndex) {
    	Optional<InboundGroupSessionBackup> sessionOpt = inboundRepo.findById(new InboundGroupSessionBackupId(userKey, sessionId, ratchetIndex));
    	
    	if (sessionOpt.isPresent()) {
    		sessionOpt.ifPresent(inboundRepo::delete);
    	} else {
    		throw new RecordNotFoundException(String.format("Inbound group session not found %s, %s, %d", userKey, sessionId, ratchetIndex));
    	}
    }

    // -------------------------
    // Outbound backup methods
    // -------------------------

    /**
     * Save outbound group session (called when new outbound session is created).
     */
    @Transactional
    public GroupSessionBackupResponse saveOutboundBackup(UUID userKey, GroupSessionBackupRequest request) {
    	if (outboundRepo.countById_UserKey(userKey) > maxOutboundSessionsLimit) {
    		throw new MaxSessionsLimitExceededException("Maximum outbound sessions limit exceeded");
    	}
    	
        OutboundGroupSessionBackup backup = new OutboundGroupSessionBackup();
        backup.setId(new OutboundGroupSessionBackupId(userKey, request.getSessionId()));
        backup.setPeerUserKey(request.getPeerUserKey());
        backup.setGroupId(request.getGroupId());
        backup.setRatchetIndex(request.getRatchetIndex());
        backup.setEncryptedSession(request.getEncryptedSession());
        backup.setAlgorithm(request.getAlgorithm());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());
        backup.setCreatedAt(Instant.now());

        OutboundGroupSessionBackup saved = outboundRepo.save(backup);
        return toResponse(saved);
    }

    /**
     * Restore outbound session by sessionId (for resuming encryption).
     */
    public Optional<GroupSessionBackupResponse> restoreOutboundSession(UUID userKey, String sessionId) {
        return outboundRepo.findById(new OutboundGroupSessionBackupId(userKey, sessionId))
                .map(entity -> {                	
                    return toResponse(entity);
                });
    }
    
    /**
     * Restore all outbound sessions by sessionId (for resuming encryption).
     */
    public List<GroupSessionBackupResponse> restoreOutboundSessions(UUID userKey) {
    	return outboundRepo.findById_UserKey(userKey)
        		.stream()
                .map(entity -> {
                	return toResponse(entity);
                })
                .toList();
    }

    /**
     * Delete stale outbound sessions (e.g., rotated or expired).
     */
    @Transactional
    public void deleteOutboundSession(UUID userKey, String sessionId) {
    	Optional<OutboundGroupSessionBackup> sessionOpt = outboundRepo.findById(new OutboundGroupSessionBackupId(userKey, sessionId));

    	if (sessionOpt.isPresent()) {
    		sessionOpt.ifPresent(outboundRepo::delete);
    	} else {
    		throw new RecordNotFoundException(String.format("Inbound group session not found %s, %s", userKey, sessionId));
    	}
    }
    
    private static GroupSessionBackupResponse toResponse(InboundGroupSessionBackup entity) {
        if (entity == null) {
            return null;
        }

        return GroupSessionBackupResponse.builder()
                .userKey(entity.getId().getUserKey())
                .sessionId(entity.getId().getSessionId())
                .ratchetIndex(entity.getId().getRatchetIndex())
                .groupId(entity.getGroupId())
                .peerUserKey(entity.getPeerUserKey())
                .encryptedSession(entity.getEncryptedSession())
                .algorithm(entity.getAlgorithm())
                .aesAlg(entity.getAesAlg())
                .salt(entity.getSalt())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .build();
    }
    
    private static GroupSessionBackupResponse toResponse(OutboundGroupSessionBackup entity) {
        if (entity == null) {
            return null;
        }

        return GroupSessionBackupResponse.builder()
                .userKey(entity.getId().getUserKey())
                .sessionId(entity.getId().getSessionId())
                .groupId(entity.getGroupId())
                .peerUserKey(entity.getPeerUserKey())
                .encryptedSession(entity.getEncryptedSession())
                .ratchetIndex(entity.getRatchetIndex())
                .algorithm(entity.getAlgorithm())
                .aesAlg(entity.getAesAlg())
                .salt(entity.getSalt())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}