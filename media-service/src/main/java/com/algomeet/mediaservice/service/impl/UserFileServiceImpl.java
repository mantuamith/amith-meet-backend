package com.algomeet.mediaservice.service.impl;

import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserFileServiceImpl implements UserFileService {
	private final UserFileRepository repository;

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
		// Owner has full access
		if (file.getOwner().equals(userKey) && file.getCleanupEligibleAt() == null) {
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

		if (file.getCleanupEligibleAt() != null) {
			throw new RuntimeException("Invalid file state, the file is in eligible for cleanup state");
		}
		
		if (!file.getOwner().equals(userKey) || !hasPermission(file, userKey, FilePermission.DELETE)) {
			throw new AccessDeniedException("Only owner can delete file");
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
			Iterator<FileAccessEntry> itAccControl = file.getAccessControlList().iterator();

			boolean isFound = false;
			while (itAccControl.hasNext()) {
				FileAccessEntry accControl = itAccControl.next();
				if (uKey.equalsIgnoreCase(accControl.getUserKey())) {
					accControl.setRefCount((accControl.getRefCount() + 1));
					isFound = true;
				} 
			}
			
			if (!isFound) {
            	Set<FilePermission> permissions = new HashSet<>();
            	permissions.add(FilePermission.SHARE);
            	permissions.add(FilePermission.READ);
            	permissions.add(FilePermission.DELETE);
            	
            	Integer refCount = 1;
            	file.getAccessControlList().add(new FileAccessEntry(uKey, refCount, permissions));
			}
		}
		
		if (file.getAccessControlList().isEmpty()) {
			// set eligible for batch job clean up
			file.setCleanupEligibleAt(Instant.now());
		}
		
		repository.save(file);		
	}

	@Override
	public void softDeleteAndMarkForCleanupIfOrphaned(String fileId, String userKey, List<String> deleteWithUserKeys) {
		UserFileDocument file = repository.findById(fileId)
				.orElseThrow(() -> new IllegalArgumentException("File not found"));

		if (!file.getOwner().equals(userKey) || !hasPermission(file, userKey, FilePermission.DELETE)) {
			throw new AccessDeniedException("Only owner can delete file");
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
