package com.algomeet.mediaservice.service.impl;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.mediaservice.service.SessionDocumentAccessTokenService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class SessionDocumentAccessTokenServiceImpl implements SessionDocumentAccessTokenService {

    private static final String SESSION_ID_CLAIM = "sid";
    private static final String FILE_ID_CLAIM = "fid";

    private final Key signingKey;
    private final Duration tokenLifetime;

    public SessionDocumentAccessTokenServiceImpl(
            @Value("${storage.document-access-url-secret}") String secret,
            @Value("${storage.document-access-url-expiration-minutes:180}") long tokenLifetimeMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.tokenLifetime = Duration.ofMinutes(Math.max(tokenLifetimeMinutes, 1));
    }

    @Override
    public String createAccessToken(String sessionId, String fileId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(FILE_ID_CLAIM, fileId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(tokenLifetime)))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public void validateAccessToken(String token, String sessionId, String fileId) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A signed access token is required");
        }

        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired access token", ex);
        }

        if (!sessionId.equals(claims.get(SESSION_ID_CLAIM, String.class))
                || !fileId.equals(claims.get(FILE_ID_CLAIM, String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token does not match the requested document");
        }
    }
}
