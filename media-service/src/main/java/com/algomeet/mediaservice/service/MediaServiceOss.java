package com.algomeet.mediaservice.service;

import java.util.UUID;

public interface MediaServiceOss extends MediaService{    
    String getReadUrl(
            String userKey,
            UUID groupId,
            String mediaId
    );
}
