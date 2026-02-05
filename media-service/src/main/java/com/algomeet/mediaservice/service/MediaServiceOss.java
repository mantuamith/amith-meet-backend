package com.algomeet.mediaservice.service;

public interface MediaServiceOss extends MediaService{    
    String getReadUrl(
            String userKey,
            String mediaId
    );
}
