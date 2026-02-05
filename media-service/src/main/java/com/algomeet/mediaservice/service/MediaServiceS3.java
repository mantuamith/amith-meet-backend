package com.algomeet.mediaservice.service;

public interface MediaServiceS3 extends MediaService{    
    String getReadUrl(
            String userKey,
            String mediaId
    );
}
