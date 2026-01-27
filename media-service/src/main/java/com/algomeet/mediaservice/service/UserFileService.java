package com.algomeet.mediaservice.service;

import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.document.FilePermission;

import java.util.List;

public interface UserFileService {

    UserFileDocument create(UserFileDocument file);

    UserFileDocument getFile(String fileId, String userKey, FilePermission permission);

    List<UserFileDocument> listMyFiles(String userKey);

    List<UserFileDocument> listFilesSharedWithMe(String userKey);

    void updateLastDownloaded(String fileId);

    void deleteFile(String fileId, String userKey);

    boolean hasPermission(UserFileDocument file, String userKey, FilePermission permission);
    
    void softDeleteAndMarkForCleanupIfOrphaned(String fileId, String userKey, List<String> deleteWithUserKeys);
    
    void shareFile(String fileId, String userKey, List<String> shareWithUserKeys);
}
