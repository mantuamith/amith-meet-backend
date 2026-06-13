package com.algomeet.mediaservice.service;

import java.util.UUID;

public interface MediaServiceS3 extends MediaService{    
    String getReadUrl(
            String userKey,
            UUID groupId,
            String mediaId
    );
}
