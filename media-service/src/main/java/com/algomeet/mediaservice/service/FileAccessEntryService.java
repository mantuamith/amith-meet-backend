package com.algomeet.mediaservice.service;

import java.util.Set;
import java.util.UUID;

import com.algomeet.mediaservice.document.FilePermission;

/**
 * Service responsible for managing file access entries and protecting the
 * lifecycle of media files.
 *
 * <p>
 * A file may be referenced by multiple users and multiple conversations.
 * This service maintains those relationships by tracking:
 * </p>
 *
 * <ul>
 *   <li>User access permissions for a file.</li>
 *   <li>Links or references from messages and conversations.</li>
 *   <li>Reference counts used to determine whether a file is still in use.</li>
 * </ul>
 *
 * <p>
 * By keeping these access entries, the service prevents premature file
 * deletion. For example, in a chat scenario where a user performs
 * <em>"delete for me"</em>, only that user's reference is removed while the
 * underlying file is preserved as long as other users or conversations still
 * reference it.
 * </p>
 *
 * <p>
 * When the last access entry is revoked and no remaining references exist,
 * the associated file becomes eligible for cleanup and physical deletion.
 * </p>
 */
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
