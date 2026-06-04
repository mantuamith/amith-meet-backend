package com.algomeet.mediaservice.service.impl;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

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
		// Owner has full access but don't show the file that is already eligible for cleanup
		if ((file.getOwner().equals(userKey) && file.getCleanupEligibleAt() == null)
				|| (file.getOwner().equals(userKey) && file.getCleanupEligibleAt().isAfter(Instant.now()))) {
			return true;
		}

		Set<FilePermission> permissions = fileAccessEntryService.getPermissions(UUID.fromString(userKey), UUID.fromString(file.getId()));

		return permissions.contains(permission);
	}

	@Override
	public void shareFile(List<String> fileIds, String userKey, List<String> shareWithUserKeys, UUID messageId) {
	    if (CollectionUtils.isEmpty(fileIds)) {
	        return;
	    }

	    // 1. Pre-parse and prepare the distinct target users
	    UUID ownerUuid = UUID.fromString(userKey);
	    Set<UUID> targetUserUuids = Optional.ofNullable(shareWithUserKeys)
	            .orElse(Collections.emptyList())
	            .stream()
	            .map(UUID::fromString)
	            .collect(Collectors.toSet());
	    
	    targetUserUuids.add(ownerUuid); // Include owner

	    // Default permissions for sharing
	    Set<FilePermission> permissions = Set.of(FilePermission.SHARE, FilePermission.READ, FilePermission.DELETE);

	    // 2. Fetch all files in a single batch query to avoid N+1 problem
	    List<UserFileDocument> files = repository.findAllById(fileIds);
	    if (files.size() != fileIds.size()) {
	        throw new IllegalArgumentException("One or more files were not found");
	    }

	    // 3. Process permissions and storage
	    for (UserFileDocument file : files) {
	        if (!hasPermission(file, userKey, FilePermission.SHARE)) {
	            throw new AccessDeniedException("User is not allowed to share the media/file: " + file.getId());
	        }

	        UUID fileUuid = UUID.fromString(file.getId());

	        for (UUID targetUserUuid : targetUserUuids) {
	            boolean isGranted = fileAccessEntryService.grantAccess(targetUserUuid, fileUuid, permissions, messageId);

	            // Double check: Do you really want to adjust storage for the owner again? 
	            // If it's a new grant for a recipient, adjust their storage.
	            if (isGranted) {
	                StorageUsageAdjustmentRequest adjustment = new StorageUsageAdjustmentRequest();
	                if (UploadContext.CHAT.name().equals(file.getUploadContext())) {
	                	adjustment.setChatStorageBytesDelta(-file.getSize());
	                	adjustment.setChatMessageCountDelta(-1L);
					} else {
						adjustment.setMediaStorageBytesDelta(-file.getSize());
						adjustment.setMediaFileCountDelta(-1L);
					}
	                userStorageUsageService.adjustUsage(targetUserUuid, adjustment);
	            }
	        }

	        // Disable auto-deletion
	        file.setCleanupEligibleAt(null);
	    }

	    // 4. Batch save all modified file documents at once
	    repository.saveAll(files);
	}

	@Override
	public void softDeleteAndMarkForCleanupIfOrphaned(String fileId, String userKey, List<String> deleteWithUserKeys, UUID messageId) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		if (!hasPermission(file, userKey, FilePermission.DELETE)) {
			throw new AccessDeniedException("User is not allowed to delete the media/file");
		}

		Set<String> forDeleteUserKeys = new HashSet<>();

		if (!CollectionUtils.isEmpty(deleteWithUserKeys)) {
			deleteWithUserKeys.forEach(ukey -> {
				forDeleteUserKeys.add(ukey);
			});
		}

		// Add owner it self if list is empty
		if(CollectionUtils.isEmpty(deleteWithUserKeys)) {
			forDeleteUserKeys.add(userKey);
		}

		for (String uKey : forDeleteUserKeys) {
			boolean isRevoked = fileAccessEntryService.revokeAccess(UUID.fromString(uKey), UUID.fromString(fileId), messageId);

			// Debit the correct quota bucket — must mirror the bucket used at upload time.
			// Files uploaded with uploadContext=CHAT were credited to chatStorageUsed;
			// everything else goes to mediaStorageUsed.
			if (isRevoked) {
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

		// Mark for clean up if file has 0 user access entry.
		if (fileAccessEntryService.countByFileId(UUID.fromString(fileId)) == 0) {
			// Set eligible for batch job clean up
			file.setCleanupEligibleAt(Instant.now());
		}

		repository.save(file);
	}	
}
