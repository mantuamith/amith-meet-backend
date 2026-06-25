package com.algomeet.mediaservice.service;

public interface SessionDocumentAccessTokenService {

    String createAccessToken(String sessionId, String fileId);

    void validateAccessToken(String token, String sessionId, String fileId);
}
