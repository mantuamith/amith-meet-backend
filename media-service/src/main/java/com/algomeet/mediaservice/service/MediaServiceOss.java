package com.algomeet.mediaservice.service;

public interface MediaServiceOss extends MediaService{    
    String getDownloadUrl(
            String userKey,
            String mediaId
    );
}
