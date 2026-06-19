package com.algomeet.mediaservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
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

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.GroupCacheService;
import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FileAccessEntryDocument;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.enums.UploadContext;
import com.algomeet.mediaservice.repository.FileAccessEntryRepository;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.FileAccessEntryService;
import com.algomeet.mediaservice.service.FileAccessPermission;
import com.algomeet.mediaservice.service.GroupFileAccessEntryService;
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
	private final GroupCacheService groupCacheService;
	private final GroupFileAccessEntryService groupFileAccessEntryService;
	private final FileAccessPermission fileAccessPermission;

	@Override
	public UserFileDocument create(UserFileDocument file) {
		file.setDateCreated(Instant.now());
		file.setDateLastModified(Instant.now());
		return repository.save(file);
	}

	@Override
	public UserFileDocument getFile(String fileId) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		return file;
	}
	
	@Override
	public UserFileDocument getFile(String fileId, String userKey, UUID groupId, FilePermission permission) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		if (!fileAccessPermission.hasPermission(file, userKey, groupId, permission)) {
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
	@Transactional(rollbackFor = Exception.class)
	public void shareFile(Set<String> fileIds, String userKey, List<String> shareWithUserKeys, UUID groupId, UUID messageId) {
	    log.info("[ShareFile] fileIds={} ownerKey={} shareWith={} groupId={} messageId={}", fileIds, userKey, shareWithUserKeys, groupId, messageId);
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
	    
	    // Check if group ID has value, this is used for sharing file(s) to group chat.
	    Group group = null;
	    if (groupId != null) {
	    	// Add group member user keys
	    	targetUserUuids.addAll(getGroupMemberKeys(groupId));	
	    	
	    	// Get group
	    	group = groupCacheService.getCachedGroup(groupId.toString());
	    }

	    Set<FilePermission> permissions = Set.of(FilePermission.SHARE, FilePermission.READ, FilePermission.DELETE);
	    
	    // Chat group-level access permissions
	    Set<FilePermission> groupPermissions = Set.of(FilePermission.SHARE, FilePermission.READ);	 

	    List<UserFileDocument> files = repository.findAllById(fileIds);
	    if (CollectionUtils.isEmpty(files)) {
	        throw new IllegalArgumentException("One or more files were not found");
	    }

	    // Track successful grants for manual rollback on failure to keep
	    // MongoDB and PostgreSQL data consistent.
	    List<CompensatingAction> successfulActions = new ArrayList<>();

	    try {
	        for (UserFileDocument file : files) {
	            if (!(fileAccessPermission.hasPermission(file, userKey, FilePermission.SHARE)
	            		|| fileAccessPermission.hasGroupPermission(group, userKey, file.getId(), FilePermission.SHARE))) {
	                throw new AccessDeniedException("User is not allowed to share the media/file: " + file.getId());
	            }

	            UUID fileUuid = UUID.fromString(file.getId());
	            
	            // If a group ID is provided, grant group-level access to the file(s).
	            // This ensures that members who join the group after the file(s) are shared
	            // can still access them without requiring individual permissions.
	            if (groupId != null) {
	            	groupFileAccessEntryService.grantAccess(groupId, UUID.fromString(file.getId()), groupPermissions);
	            }

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
	        UUID groupId,
	        UUID messageId) {
		
		softDeleteAndMarkForCleanupIfOrphaned(
		        fileIds,
		        userKey,
		        deleteWithUserKeys,
		        groupId,
		        messageId,
		        false);
	}

	@Override
	public void softDeleteAndMarkForCleanupIfOrphaned(
	        Set<String> fileIds,
	        String userKey,
	        Set<String> deleteWithUserKeys,
	        UUID groupId,
	        UUID messageId,
	        boolean performedByAdmin) {

		Set<String> forDeleteUserKeys = new HashSet<>();
		if (!CollectionUtils.isEmpty(deleteWithUserKeys)) {
			forDeleteUserKeys.addAll(deleteWithUserKeys);
		}
		
		// If group ID is pass
		if (groupId != null) {
			// Add group member user keys
			forDeleteUserKeys.addAll(getGroupMemberStrKeys(groupId));
		}
		
	    // A "delete for everyone" intent is signalled by the caller explicitly listing
	    // more than one user (sender + at least one recipient), or by listing someone
	    // other than the caller themselves.
		/*
	    boolean isDeletingForEveryone = forDeleteUserKeys != null
	            && (forDeleteUserKeys.size() > 1
	                || (forDeleteUserKeys.size() == 1 && !forDeleteUserKeys.contains(userKey)));
	    */

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
	        boolean canDeleteForOthers = performedByAdmin 
	        		? performedByAdmin : fileAccessPermission.hasPermission(file, userKey, FilePermission.DELETE);
            
	        for (String targetUserKey : forDeleteUserKeys) {
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
	            		softDeleteAndMarkForCleanupIfOrphanedAcl(file, userKey, Set.of(targetUserId.toString()), performedByAdmin);
	            	}
	            }
	        }

	        // Mark the file for cleanup only when:
	        //  (a) the caller intended to delete for EVERYONE (not just "for me"), AND
	        //  (b) no more access entries remain.
	        // Guarding on (a) prevents a "delete for me" from the owner silently
	        // expiring the file for all other participants when shareFile() was never
	        // called (countByFileId == 0 even though recipients should still have access).
	        
	        /*
	         * Protection against deleting files that still have active references is already
	         * enforced by fileAccessEntryService.countByFileId().
	         *
	         * The additional conditions below (isDeletingForEveryone && canDeleteForOthers)
	         * would prevent incremental revocation of file access. For example, in a 1:1
	         * conversation, the sender may delete the chat for themselves first, and the
	         * receiver may delete it later. Access entries are revoked independently, and
	         * the file should only be marked for cleanup once no references remain.
	         */
	        if (/*isDeletingForEveryone
	        		&& canDeleteForOthers
	        		&&*/ fileAccessEntryService.countByFileId(fileId) == 0
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
	public void softDeleteAndMarkForCleanupIfOrphanedAcl(UserFileDocument file, String userKey, Set<String> deleteWithUserKeys,
	        boolean performedByAdmin) {
		if (!performedByAdmin && !fileAccessPermission.hasPermissionAcl(file, userKey, FilePermission.DELETE)) {
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
	
	private Set<UUID> getGroupMemberKeys(UUID groupId) {
		Group group = groupCacheService.getCachedGroup(groupId.toString());

		if (group != null && !CollectionUtils.isEmpty(group.getMembers())) {
			return group.getMembers().stream()
					.map(m -> UUID.fromString(m.getUserKey()))
					.collect(Collectors.toSet());
		}

		return Set.of();
	}
	
	private Set<String> getGroupMemberStrKeys(UUID groupId) {
		Group group = groupCacheService.getCachedGroup(groupId.toString());

		if (group != null && !CollectionUtils.isEmpty(group.getMembers())) {
			return group.getMembers().stream()
					.map(m -> m.getUserKey())
					.collect(Collectors.toSet());
		}

		return Set.of();
	}
}
