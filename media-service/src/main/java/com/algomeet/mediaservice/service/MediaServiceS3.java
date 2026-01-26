package com.algomeet.mediaservice.service;

public interface MediaServiceS3 extends MediaService{    
    String getDownloadUrl(
            String userKey,
            String mediaId
    );
}
