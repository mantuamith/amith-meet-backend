package com.algomeet.signalingservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalingservice.entity.OutboundGroupSessionBackup;
import com.algomeet.signalingservice.entity.OutboundGroupSessionBackupId;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalingservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalingservice.entity.InboundGroupSessionBackupId;
import com.algomeet.signalingservice.entity.InboundGroupSessionBackup;
import com.algomeet.signalingservice.repository.OutboundGroupSessionBackupRepository;

import lombok.AllArgsConstructor;

import com.algomeet.signalingservice.repository.InboundGroupSessionBackupRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Service
public class GroupSessionBackupService {
    private final InboundGroupSessionBackupRepository inboundRepo;
    private final OutboundGroupSessionBackupRepository outboundRepo;

    // -------------------------
    // Inbound backup methods
    // -------------------------

    @Transactional
    public GroupSessionBackupResponse saveInboundBackup(UUID userKey, GroupSessionBackupRequest request) {
        InboundGroupSessionBackup backup = new InboundGroupSessionBackup();
        backup.getId().setUserKey(userKey);
        backup.getId().setSessionId(request.getSessionId());
        backup.getId().setRatchetIndex(request.getRatchetIndex());
        backup.setPeerUserKey(request.getPeerUserKey());
        backup.setGroupId(request.getGroupId());
        backup.setEncryptedSession(request.getEncryptedSession());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());
        backup.setCreatedAt(Instant.now());

        InboundGroupSessionBackup saved = inboundRepo.save(backup);
        return GroupSessionBackupResponse.builder()
        		.userKey(saved.getId().getUserKey())
        		.sessionId(saved.getId().getSessionId())
        		.ratchetIndex(saved.getId().getRatchetIndex())
        		.peerUserKey(saved.getPeerUserKey())
        		.groupId(saved.getGroupId())
        		.encryptedSession(saved.getEncryptedSession())
        		.aesAlg(saved.getAesAlg())
        		.salt(saved.getSalt())
        		.version(saved.getVersion())
        		.build();
    }

    /**
     * Restore inbound session by sessionId (for resuming encryption).
     */
    public Optional<GroupSessionBackupResponse> restoreInboundSession(UUID userKey, Integer ratchetIndex, String sessionId) {
        return inboundRepo.findClosestBackup(userKey, sessionId, ratchetIndex)
                .map(entity -> {
                	GroupSessionBackupResponse dto = GroupSessionBackupResponse.builder()
                    .userKey(entity.getId().getUserKey())
                    .sessionId(entity.getId().getSessionId())
                    .ratchetIndex(entity.getId().getRatchetIndex())
                    .peerUserKey(entity.getPeerUserKey())
                    .groupId(entity.getGroupId())
                    .encryptedSession(entity.getEncryptedSession())
                    .aesAlg(entity.getAesAlg())
                    .salt(entity.getSalt())
                    .version(entity.getVersion())
                    .build();
                    return dto;
                });
    }
    
    /**
     * Restore all inbound sessions by sessionId (for resuming encryption).
     */
    public List<GroupSessionBackupResponse> restoreInboundSessions(UUID userKey) {
        return inboundRepo.findHighestRatchetIndexByUserKeyGroupedBySessionId(userKey)
        		.stream()
                .map(entity -> {
                	GroupSessionBackupResponse dto = GroupSessionBackupResponse.builder()
                    .userKey(entity.getId().getUserKey())
                    .sessionId(entity.getId().getSessionId())
                    .ratchetIndex(entity.getId().getRatchetIndex())
                    .peerUserKey(entity.getPeerUserKey())
                    .groupId(entity.getGroupId())
                    .encryptedSession(entity.getEncryptedSession())
                    .aesAlg(entity.getAesAlg())
                    .salt(entity.getSalt())
                    .version(entity.getVersion())
                    .build();
                    return dto;
                })
                .toList();
    }

    @Transactional
    public void pruneInboundBackups(UUID userKey, String sessionId, int keepLastN) {
        List<InboundGroupSessionBackup> backups = inboundRepo.findByUserKey(userKey)
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
        OutboundGroupSessionBackup backup = new OutboundGroupSessionBackup();
        backup.getId().setUserKey(userKey);
        backup.getId().setSessionId(request.getSessionId());
        backup.setPeerUserKey(request.getPeerUserKey());
        backup.setGroupId(request.getGroupId());
        backup.setEncryptedSession(request.getEncryptedSession());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());
        backup.setCreatedAt(Instant.now());

        OutboundGroupSessionBackup saved = outboundRepo.save(backup);
        return GroupSessionBackupResponse.builder()
                .userKey(saved.getId().getUserKey())
                .sessionId(saved.getId().getSessionId())
                .groupId(saved.getGroupId())
                .peerUserKey(saved.getPeerUserKey())
                .encryptedSession(saved.getEncryptedSession())
                .aesAlg(saved.getAesAlg())
                .salt(saved.getSalt())
                .version(saved.getVersion())
                .build();
    }

    /**
     * Restore outbound session by sessionId (for resuming encryption).
     */
    public Optional<GroupSessionBackupResponse> restoreOutboundSession(UUID userKey, String sessionId) {
        return outboundRepo.findById(new OutboundGroupSessionBackupId(userKey, sessionId))
                .map(entity -> {
                	GroupSessionBackupResponse dto = GroupSessionBackupResponse.builder()
                    .userKey(entity.getId().getUserKey())
                    .sessionId(entity.getId().getSessionId())
                    .groupId(entity.getGroupId())
                    .peerUserKey(entity.getPeerUserKey())
                    .encryptedSession(entity.getEncryptedSession())
                    .aesAlg(entity.getAesAlg())
                    .salt(entity.getSalt())
                    .version(entity.getVersion())
                    .build();
                    return dto;
                });
    }
    
    /**
     * Restore all outbound sessions by sessionId (for resuming encryption).
     */
    public List<GroupSessionBackupResponse> restoreOutboundSessions(UUID userKey) {
    	return outboundRepo.findByUserKey(userKey)
        		.stream()
                .map(entity -> {
                	GroupSessionBackupResponse dto = GroupSessionBackupResponse.builder()
                    .userKey(entity.getId().getUserKey())
                    .sessionId(entity.getId().getSessionId())
                    .groupId(entity.getGroupId())
                    .peerUserKey(entity.getPeerUserKey())
                    .encryptedSession(entity.getEncryptedSession())
                    .aesAlg(entity.getAesAlg())
                    .salt(entity.getSalt())
                    .version(entity.getVersion())
                    .build();
                    return dto;
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
}