package com.algomeet.mediaservice.service.impl;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.Group;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.service.FileAccessEntryService;
import com.algomeet.mediaservice.service.FileAccessPermission;
import com.algomeet.mediaservice.service.GroupFileAccessEntryService;
import com.algomeet.mediaservice.util.SearchUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessPermissionImpl implements FileAccessPermission{
	private final FileAccessEntryService fileAccessEntryService;
	private final AbstractGroupCache groupCacheService;
	private final GroupFileAccessEntryService groupFileAccessEntryService;
	
	@Override
	public boolean hasPermission(UserFileDocument file, String userKey, FilePermission permission) {
		return hasPermission(file, userKey, null, permission);
	}
	
	public boolean hasPermission(UserFileDocument file, String userKey, UUID groupId, FilePermission permission) {
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
		
		// 6. Check for chat group permissions
		Set<FilePermission> groupPermissions = null;
		if (!(granted) && groupId != null) {
			groupPermissions = groupFileAccessEntryService.getPermissions(groupId, UUID.fromString(file.getId()));			
			granted = groupPermissions.contains(permission) && isGroupMember(groupId, userKey);
		}
		
		log.info("[HasPermission] {} fileId={} userKey={} permission={} reason=FILE_ACCESS_ENTRY entryPermissions={}, groupPermissions={}",
		        granted ? "GRANTED" : "DENIED", file.getId(), userKey, permission, permissions, groupPermissions);
		
		return granted;
	}
	
	public boolean hasGroupPermission(Group group, String userKey, String fileId, FilePermission permission) {
		if (group == null) {
			return false;
		}
		
		Set<FilePermission> groupPermissions = groupFileAccessEntryService.getPermissions(group.getId(), UUID.fromString(fileId));			
		return groupPermissions.contains(permission) && isGroupMember(group, userKey);
	}
	
	@Deprecated
	public boolean hasPermissionAcl(UserFileDocument file, String userKey, FilePermission permission) {
		// Null-safe check for the ACL list
	    if (file.getAccessControlList() == null) {
	        return false;
	    }
	    
	    if (userKey.equals(file.getOwner())) {
	    	return true;
	    }

	    // Optimized, null-safe Stream
	    return file.getAccessControlList().stream()
	            .filter(entry -> entry != null && userKey.equals(entry.getUserKey()))
	            .anyMatch(entry -> entry.getPermissions() != null && entry.getPermissions().contains(permission));
	}
	
	public boolean isGroupMember(UUID groupId, String userKey) {
		Group group = groupCacheService.getCachedGroup(groupId.toString());
		return SearchUtil.findMember(group, userKey).isPresent();
	}
	
	public boolean isGroupMember(Group group, String userKey) {
		return SearchUtil.findMember(group, userKey).isPresent();
	}
}
