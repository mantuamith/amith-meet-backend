package com.algomeet.mediaservice.service;

import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.document.FilePermission;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserFileService {

    UserFileDocument create(UserFileDocument file);

    UserFileDocument getFile(String fileId, String userKey, FilePermission permission);

    List<UserFileDocument> listMyFiles(String userKey);

    List<UserFileDocument> listFilesSharedWithMe(String userKey);

    void updateLastRead(String fileId);

    void deleteFile(String fileId, String userKey);

    boolean hasPermission(UserFileDocument file, String userKey, FilePermission permission);
    
    void softDeleteAndMarkForCleanupIfOrphaned(Set<String> fileIds, String userKey, Set<String> deleteWithUserKeys, UUID messageId);
    
    void shareFile(Set<String> fileIds, String userKey, List<String> shareWithUserKeys, UUID messageId);
}
