package com.algomeet.mediaservice.service;

import java.util.UUID;

import com.algomeet.mediaservice.document.UserFileDocument;

public interface MediaServiceS3 extends MediaService{    
    String getReadUrl(
            String userKey,
            UUID groupId,
            String mediaId
    );
    
    String getReadUrl(UserFileDocument fileDoc, String userKey, UUID groupId, String mediaId);
}
