package com.algomeet.mediaservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FileAccessEntryDocument;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.enums.UploadContext;
import com.algomeet.mediaservice.repository.FileAccessEntryRepository;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.FileAccessEntryService;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFileServiceImpl implements UserFileService {
	private final UserFileRepository repository;
	private final UserStorageUsageService userStorageUsageService;
	private final FileAccessEntryService fileAccessEntryService;
	private final FileAccessEntryRepository fileAccessEntryRepository;

	@Override
	public UserFileDocument create(UserFileDocument file) {
		file.setDateCreated(Instant.now());
		file.setDateLastModified(Instant.now());
		return repository.save(file);
	}

	@Override
	public UserFileDocument getFile(String fileId, String userKey, FilePermission permission) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		if (!hasPermission(file, userKey, permission)) {
			throw new AccessDeniedException("Permission denied: " + permission);
		}

		return file;
	}

	@Override
	public List<UserFileDocument> listMyFiles(String userId) {
		return repository.findByOwner(userId);
	}

	@Override
	public List<UserFileDocument> listFilesSharedWithMe(String userKey) {
		List<FileAccessEntryDocument> list = fileAccessEntryRepository.findByUserKey(UUID.fromString(userKey));
		return repository.findAllById(list.stream().map(fa -> fa.getFileId().toString()).toList());
	}

	@Override
	public void updateLastRead(String fileId) {
		repository.findById(fileId).ifPresent(file -> {
			file.setDateLastRead(Instant.now());
			repository.save(file);
		});
	}

	@Override
	public void deleteFile(String fileId, String userId) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		if (!(file.getOwner().compareTo(userId) == 0)) {
			throw new AccessDeniedException("Only owner can delete file");
		}
		
		// Delete all file access entries
		fileAccessEntryService.deleteByFileId(UUID.fromString(fileId));
		
		// Delete the file metadata
		repository.deleteById(fileId);
	}

	@Override
	public boolean hasPermission(UserFileDocument file, String userKey, FilePermission permission) {
		// 1. Determine if the file is currently expired/scheduled for cleanup
	    boolean isExpired = file.getCleanupEligibleAt() != null
	            && file.getCleanupEligibleAt().isBefore(Instant.now());

	    // 2. Check Ownership (Safe against NullPointerException)
	    boolean isOwner = userKey.equals(file.getOwner());

	    if (isExpired) {
	        log.info("[HasPermission] DENIED fileId={} userKey={} permission={} reason=EXPIRED cleanupEligibleAt={}",
	                file.getId(), userKey, permission, file.getCleanupEligibleAt());
	        return false;
	    }

	    // 3. If not expired, Owner has full wildcard access to everything
	    if (isOwner) {
	        log.debug("[HasPermission] GRANTED fileId={} userKey={} permission={} reason=OWNER", file.getId(), userKey, permission);
	        return true;
	    }

	    // 4. Chat attachments — only participants who have an active FileAccessEntry may
	    //    READ a file shared in a conversation. The conversationId is used as a signal
	    //    that the file is chat-attached, but we still require an explicit access entry
	    //    so that non-participants cannot read files just by guessing a mediaId.
	    if (permission == FilePermission.READ
	            && file.getConversationId() != null
	            && !file.getConversationId().isBlank()) {
	        log.debug("[HasPermission] GRANTED fileId={} userKey={} permission={} reason=CONVERSATION_ID conversationId={}",
	                file.getId(), userKey, permission, file.getConversationId());

	        return fileAccessEntryService.hasAccess(UUID.fromString(userKey), UUID.fromString(file.getId()));
	    }

	    // Backward compatibility
	    if (!CollectionUtils.isEmpty(file.getAccessControlList())) {
	    	if(hasPermissionAcl(file, userKey, permission)) {
	    	    log.debug("[HasPermission] GRANTED fileId={} userKey={} permission={} reason=ACL", file.getId(), userKey, permission);
	    		return true;
	    	}
	    }

	    // 5. Fallback to Access Control Matrix (MongoDB) for shared users
		Set<FilePermission> permissions = fileAccessEntryService.getPermissions(UUID.fromString(userKey), UUID.fromString(file.getId()));
		boolean granted = permissions.contains(permission);
		log.info("[HasPermission] {} fileId={} userKey={} permission={} reason=FILE_ACCESS_ENTRY entryPermissions={}",
		        granted ? "GRANTED" : "DENIED", file.getId(), userKey, permission, permissions);
		return granted;
	}
	
	@Deprecated
	public boolean hasPermissionAcl(UserFileDocument file, String userKey, FilePermission permission) {
		// Null-safe check for the ACL list
	    if (file.getAccessControlList() == null) {
	        return false;
	    }

	    // Optimized, null-safe Stream
	    return file.getAccessControlList().stream()
	            .filter(entry -> entry != null && userKey.equals(entry.getUserKey()))
	            .anyMatch(entry -> entry.getPermissions() != null && entry.getPermissions().contains(permission));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void shareFile(Set<String> fileIds, String userKey, List<String> shareWithUserKeys, UUID messageId) {
	    log.info("[ShareFile] fileIds={} ownerKey={} shareWith={} messageId={}", fileIds, userKey, shareWithUserKeys, messageId);
	    if (CollectionUtils.isEmpty(fileIds)) {
	        return;
	    }

	    UUID ownerUuid = UUID.fromString(userKey);
	    Set<UUID> targetUserUuids = Optional.ofNullable(shareWithUserKeys)
	            .orElse(Collections.emptyList())
	            .stream()
	            .map(UUID::fromString)
	            .collect(Collectors.toSet());
	    targetUserUuids.add(ownerUuid);

	    Set<FilePermission> permissions = Set.of(FilePermission.SHARE, FilePermission.READ, FilePermission.DELETE);

	    List<UserFileDocument> files = repository.findAllById(fileIds);
	    if (CollectionUtils.isEmpty(files)) {
	        throw new IllegalArgumentException("One or more files were not found");
	    }

	    // Track successful grants for manual rollback on failure to keep
	    // MongoDB and PostgreSQL data consistent.
	    List<CompensatingAction> successfulActions = new ArrayList<>();

	    try {
	        for (UserFileDocument file : files) {
	            if (!hasPermission(file, userKey, FilePermission.SHARE)) {
	                throw new AccessDeniedException("User is not allowed to share the media/file: " + file.getId());
	            }

	            UUID fileUuid = UUID.fromString(file.getId());

	            for (UUID targetUserUuid : targetUserUuids) {
	                // Modifies MongoDB
	                boolean isGranted = fileAccessEntryService.grantAccess(targetUserUuid, fileUuid, permissions, messageId);

	                if (isGranted) {
	                    StorageUsageAdjustmentRequest adjustment = new StorageUsageAdjustmentRequest();
	                    if (UploadContext.CHAT.name().equals(file.getUploadContext())) {
	                        adjustment.setChatStorageBytesDelta(file.getSize());
	                        adjustment.setChatMessageCountDelta(1L);
	                    } else {
	                        adjustment.setMediaStorageBytesDelta(file.getSize());
	                        adjustment.setMediaFileCountDelta(1L);
	                    }
	                    
	                    // Modifies External Storage Service
	                    userStorageUsageService.adjustUsage(targetUserUuid, adjustment);
	                    
	                    // Record that BOTH MongoDB and Quotas were updated for this user/file combo
	                    successfulActions.add(new CompensatingAction(targetUserUuid, fileUuid, file));
	                }
	            }
	            file.setCleanupEligibleAt(null);
	        }
	        repository.saveAll(files);

	    } catch (Exception ex) {
	        log.error("Error encountered during batch file sharing. Initiating compensating actions for MongoDB and Storage quotas.", ex);
	        
	        // Reverse actions in backward order
	        for (int i = successfulActions.size() - 1; i >= 0; i--) {
	            CompensatingAction action = successfulActions.get(i);
	            try {
	                // 1. Manually roll back MongoDB permissions safely
	                fileAccessEntryService.revokeAccess(action.userId, action.fileId, messageId);
	                
	                // 2. Manually roll back the quota adjustment
	                StorageUsageAdjustmentRequest rollbackAdjustment = new StorageUsageAdjustmentRequest();
	                if (UploadContext.CHAT.name().equals(action.file.getUploadContext())) {
	                    rollbackAdjustment.setChatStorageBytesDelta(-action.file.getSize());
	                    rollbackAdjustment.setChatMessageCountDelta(-1L);
	                } else {
	                    rollbackAdjustment.setMediaStorageBytesDelta(-action.file.getSize());
	                    rollbackAdjustment.setMediaFileCountDelta(-1L);
	                }
	                userStorageUsageService.adjustUsage(action.userId, rollbackAdjustment);
	                
	            } catch (Exception rollbackEx) {
	                log.error("CRITICAL: Failed to reverse changes for user: {} and file: {}", 
	                        action.userId, action.fileId, rollbackEx);
	            }
	        }
	        throw ex; 
	    }
	}

	private static class CompensatingAction {
	    final UUID userId;
	    final UUID fileId;
	    final UserFileDocument file;

	    CompensatingAction(UUID userId, UUID fileId, UserFileDocument file) {
	        this.userId = userId;
	        this.fileId = fileId;
	        this.file = file;
	    }
	}

	@Override
	public void softDeleteAndMarkForCleanupIfOrphaned(
	        Set<String> fileIds,
	        String userKey,
	        Set<String> deleteWithUserKeys,
	        UUID messageId) {

	    // A "delete for everyone" intent is signalled by the caller explicitly listing
	    // more than one user (sender + at least one recipient), or by listing someone
	    // other than the caller themselves.
	    boolean isDeletingForEveryone = deleteWithUserKeys != null
	            && (deleteWithUserKeys.size() > 1
	                || (deleteWithUserKeys.size() == 1 && !deleteWithUserKeys.contains(userKey)));

	    if (CollectionUtils.isEmpty(fileIds)) {
	        return;
	    }

	    // Batch load all files to avoid N+1 database queries.
	    List<UserFileDocument> files = repository.findAllById(fileIds);

	    if (CollectionUtils.isEmpty(files)) {
	        throw new IllegalArgumentException("One or more files were not found");
	    }

	    List<UserFileDocument> modifiedFiles = new ArrayList<>();
	    for (UserFileDocument file : files) {  	    		   	
	    	
	        UUID fileId = UUID.fromString(file.getId());

	        // DELETE permission allows revoking access for any user; otherwise,
	        // users may revoke only their own access.
	        // In batch deletes, permission failures for other users should not stop
	        // processing so the caller's own access link can still be removed and
	        // orphaned files can be cleaned up.
	        boolean canDeleteForOthers = hasPermission(file, userKey, FilePermission.DELETE);

	        for (String targetUserKey : deleteWithUserKeys) {
	            boolean deletingOtherUser =
	                    userKey != null && !userKey.equals(targetUserKey);

	            if (deletingOtherUser && !canDeleteForOthers) {
	                continue;
	            }

	            UUID targetUserId = UUID.fromString(targetUserKey);

	            boolean revoked =
	                    fileAccessEntryService.revokeAccess(targetUserId, fileId, messageId);

	            if (revoked) {
	                adjustUserStorageUsage(targetUserId, file);
	            }	            	
	            
	            
	            // Support backward compatibility
	            // New logic has safety net using messageId, that's why it must be executed first.
	            if(!revoked) {
	            	if(!CollectionUtils.isEmpty(file.getAccessControlList())) {
	            		softDeleteAndMarkForCleanupIfOrphanedAcl(file, userKey, Set.of(targetUserId.toString()));
	            	}
	            }
	        }

	        // Mark the file for cleanup only when:
	        //  (a) the caller intended to delete for EVERYONE (not just "for me"), AND
	        //  (b) no more access entries remain.
	        // Guarding on (a) prevents a "delete for me" from the owner silently
	        // expiring the file for all other participants when shareFile() was never
	        // called (countByFileId == 0 even though recipients should still have access).
	        if (isDeletingForEveryone
	        		&& canDeleteForOthers
	        		&& fileAccessEntryService.countByFileId(fileId) == 0
	        		// Backward compatibility
	        		&& CollectionUtils.isEmpty(file.getAccessControlList())) {
	            file.setCleanupEligibleAt(Instant.now());
	            modifiedFiles.add(file);
	        }
	    }

	    // Persist all modified documents in a single batch operation.
	    repository.saveAll(modifiedFiles);
	}

	/**
	 * Updates the user's storage quota after a file access entry is removed.
	 * The quota bucket depends on the file upload context.
	 */
	private void adjustUserStorageUsage(UUID userId, UserFileDocument file) {

	    StorageUsageAdjustmentRequest request = new StorageUsageAdjustmentRequest();

	    if (UploadContext.CHAT.name().equals(file.getUploadContext())) {
	        request.setChatStorageBytesDelta(-file.getSize());
	        request.setChatMessageCountDelta(-1L);
	    } else {
	        request.setMediaStorageBytesDelta(-file.getSize());
	        request.setMediaFileCountDelta(-1L);
	    }

	    userStorageUsageService.adjustUsage(userId, request);
	}
	
	@Deprecated
	public void softDeleteAndMarkForCleanupIfOrphanedAcl(UserFileDocument file, String userKey, Set<String> deleteWithUserKeys) {
		if (!hasPermissionAcl(file, userKey, FilePermission.DELETE)) {
			return;
		}
        
		for (String uKey : deleteWithUserKeys) {
			Iterator<FileAccessEntry> itAccControl = file.getAccessControlList().iterator();

			while (itAccControl.hasNext()) {
				FileAccessEntry accControl = itAccControl.next();
				if (uKey.equalsIgnoreCase(accControl.getUserKey())) {
					accControl.setRefCount((accControl.getRefCount() - 1));
				}

				if (accControl.getRefCount() <= 0) {
					itAccControl.remove();

					// Debit the correct quota bucket — must mirror the bucket used at upload time.
					// Files uploaded with uploadContext=CHAT were credited to chatStorageUsed;
					// everything else goes to mediaStorageUsed.
					StorageUsageAdjustmentRequest storageUsageAdjustment = new StorageUsageAdjustmentRequest();
					if (UploadContext.CHAT.name().equals(file.getUploadContext())) {
						storageUsageAdjustment.setChatStorageBytesDelta(-file.getSize());
						storageUsageAdjustment.setChatMessageCountDelta(-1L);
					} else {
						storageUsageAdjustment.setMediaStorageBytesDelta(-file.getSize());
						storageUsageAdjustment.setMediaFileCountDelta(-1L);
					}
					userStorageUsageService.adjustUsage(UUID.fromString(uKey), storageUsageAdjustment);
				}
			}
		}
		
		if (CollectionUtils.isEmpty(file.getAccessControlList())) {
			// set eligible for batch job clean up
			file.setCleanupEligibleAt(Instant.now());
		}

		repository.save(file);
	}
}
