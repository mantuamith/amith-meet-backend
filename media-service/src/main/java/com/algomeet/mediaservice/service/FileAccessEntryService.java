package com.algomeet.mediaservice.service;

import java.util.Set;
import java.util.UUID;

import com.algomeet.mediaservice.document.FilePermission;

public interface FileAccessEntryService {
	 public boolean grantAccess(UUID userKey, UUID fileId, Set<FilePermission> permissions, UUID messageId);
	    /**
	     * Atomically revokes/decrements access. When refCount drops to or below 0, 
	     * the permission document is cleanly purged from disk.
	     */
	    public boolean revokeAccess(UUID userKey, UUID fileId, UUID messageId);

	    /**
	     * Direct check to verify access vectors
	     */
	    public boolean hasAccess(UUID userKey, UUID fileId);

	    /**
	     * Fetch all active permissions for a target user-file match
	     */
	    public Set<FilePermission> getPermissions(UUID userKey, UUID fileId);

	    public long countByFileId(UUID fileId);
	    public long deleteByFileId(UUID fileId);
}
