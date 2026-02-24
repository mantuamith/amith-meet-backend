package com.algomeet.mediaservice.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserFileServiceImpl implements UserFileService {
	private final UserFileRepository repository;
	private final UserStorageUsageService userStorageUsageService;

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
	public List<UserFileDocument> listFilesSharedWithMe(String userId) {
		return repository.findFilesUserHasAccessTo(userId);
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

		if (!file.getOwner().equals(userId)) {
			throw new AccessDeniedException("Only owner can delete file");
		}

		repository.deleteById(fileId);
	}

	@Override
	public boolean hasPermission(UserFileDocument file, String userKey, FilePermission permission) {
		
		// Owner has full access but don't show the file that is already eligible for cleanup
		if ((file.getOwner().equals(userKey) && file.getCleanupEligibleAt() == null)
				|| (file.getOwner().equals(userKey) && file.getCleanupEligibleAt().isAfter(Instant.now()))) {
			return true;
		}

		if (file.getAccessControlList() == null) {
			return false;
		}

		return file.getAccessControlList().stream().filter(entry -> entry.getUserKey().equals(userKey))
				.map(FileAccessEntry::getPermissions).anyMatch(perms -> perms.contains(permission));
	}

	@Override
	public void shareFile(String fileId, String userKey, List<String> shareWithUserKeys) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		if (!hasPermission(file, userKey, FilePermission.SHARE)) {
			throw new AccessDeniedException("User is not allowed to share the media/file");
		}

		Set<String> forShareUserKeys = new HashSet<>();

		if (!CollectionUtils.isEmpty(shareWithUserKeys)) {
			shareWithUserKeys.forEach(ukey -> {
				forShareUserKeys.add(ukey);
			});
		}

		// Add owner it self
		forShareUserKeys.add(userKey);

		for (String uKey : forShareUserKeys) {
			boolean isFound = false;

			if (!CollectionUtils.isEmpty(file.getAccessControlList())) {
				Iterator<FileAccessEntry> itAccControl = file.getAccessControlList().iterator();

				while (itAccControl.hasNext()) {
					FileAccessEntry accControl = itAccControl.next();
					if (uKey.equalsIgnoreCase(accControl.getUserKey())) {
						accControl.setRefCount((accControl.getRefCount() + 1));
						isFound = true;
					}
				}
			}

			if (!isFound) {
				Set<FilePermission> permissions = new HashSet<>();
				permissions.add(FilePermission.SHARE);
				permissions.add(FilePermission.READ);
				permissions.add(FilePermission.DELETE);

				Integer refCount = 1;
			
				// Check if empty
				if (CollectionUtils.isEmpty(file.getAccessControlList())) {
					file.setAccessControlList(new ArrayList<>());
				} 

				file.getAccessControlList().add(new FileAccessEntry(uKey, refCount, permissions));
				
            	// Update user storage usage, add the shared file count and file size            	
            	StorageUsageAdjustmentRequest storageUsageAdjustment = new StorageUsageAdjustmentRequest();
            	storageUsageAdjustment.setMediaFileCountDelta(1L);
            	storageUsageAdjustment.setMediaStorageBytesDelta(file.getSize());
            	userStorageUsageService.adjustUsage(UUID.fromString(uKey), storageUsageAdjustment);

			}
		}

		// Set clean up date to null to disable auto deletion
		file.setCleanupEligibleAt(null);
		repository.save(file);
	}

	@Override
	public void softDeleteAndMarkForCleanupIfOrphaned(String fileId, String userKey, List<String> deleteWithUserKeys) {
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

		// Add owner it self
		forDeleteUserKeys.add(userKey);

		for (String uKey : forDeleteUserKeys) {
			Iterator<FileAccessEntry> itAccControl = file.getAccessControlList().iterator();

			while (itAccControl.hasNext()) {
				FileAccessEntry accControl = itAccControl.next();
				if (uKey.equalsIgnoreCase(accControl.getUserKey())) {
					accControl.setRefCount((accControl.getRefCount() - 1));
				}

				if (accControl.getRefCount() <= 0) {
					itAccControl.remove();
					
	            	// Update user storage usage, subtract the deleted file count and file size     	
	            	StorageUsageAdjustmentRequest storageUsageAdjustment = new StorageUsageAdjustmentRequest();
	            	storageUsageAdjustment.setMediaFileCountDelta(-1L);
	            	storageUsageAdjustment.setMediaStorageBytesDelta(-file.getSize());
	            	userStorageUsageService.adjustUsage(UUID.fromString(uKey), storageUsageAdjustment);
				}
			}
		}
		if (file.getAccessControlList().isEmpty()) {
			// set eligible for batch job clean up
			file.setCleanupEligibleAt(Instant.now());
		}

		repository.save(file);
	}
}
