package com.algomeet.mediaservice.service;

import java.util.Set;
import java.util.UUID;

import com.algomeet.mediaservice.document.FilePermission;

public interface GroupFileAccessEntryService {
	 public boolean grantAccess(UUID groupId, UUID fileId, Set<FilePermission> permissions);
	    /**
	     * Atomically revokes/decrements access. When refCount drops to or below 0, 
	     * the permission document is cleanly purged from disk.
	     */
	    public void revokeAccess(UUID groupId, UUID fileId);


	    /**
	     * Fetch all active permissions for a target user-file match
	     */
	    public Set<FilePermission> getPermissions(UUID groupId, UUID fileId);
}
