package com.algomeet.mediaservice.service;

import java.util.UUID;

import com.algomeet.common.dto.Group;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;

public interface FileAccessPermission {
    boolean hasPermission(UserFileDocument file, String userKey, FilePermission permission);
    
    boolean hasPermission(UserFileDocument file, String userKey, UUID groupId, FilePermission permission);
    
    public boolean hasGroupPermission(Group group, String userKey, String fileId, FilePermission permission);
	
	@Deprecated
	boolean hasPermissionAcl(UserFileDocument file, String userKey, FilePermission permission);
	
	boolean isGroupMember(UUID groupId, String userKey);
	
	boolean isGroupMember(Group group, String userKey);
}
