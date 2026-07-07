package com.algomeet.mediaservice.security;

import java.security.PublicKey;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.algomeet.mediaservice.config.ProsodyJwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProsodyFileSharingTokenService {

    private final ProsodyJwtProperties properties;
    private final ProsodyPublicKeyLoader publicKeyLoader;

    /**
     * @throws JwtException if the token is malformed, expired, or does not carry the expected
     *                       {@code aud}/{@code iss} claims for Prosody file-sharing.
     */
    public ProsodyFileSharingPrincipal parseAndValidate(String token) {
        if (!properties.isEnabled()) {
            throw new JwtException("Prosody file-sharing JWT verification is disabled");
        }
        PublicKey publicKey = publicKeyLoader.getPublicKey();
        if (publicKey == null) {
            throw new JwtException("Prosody public key is not available");
        }

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseClaimsJws(token)
                .getBody();

        if (!hasAudience(claims, properties.getAudience())) {
            throw new JwtException("Token audience does not include '" + properties.getAudience() + "'");
        }

        String meetingId = claims.get("meeting_id", String.class);
        if (meetingId == null || meetingId.isBlank()) {
            meetingId = claims.get("room", String.class);
        }

        Map<String, Object> context = claims.get("context", Map.class);
        String userId = null;
        String displayName = null;
        String affiliation = null;
        boolean fileUploadEnabled = false;

        if (context != null) {
            Object userObj = context.get("user");
            if (userObj instanceof Map<?, ?> user) {
                userId = asString(user.get("id"));
                displayName = asString(user.get("name"));
                affiliation = asString(user.get("affiliation"));
            }
            Object featuresObj = context.get("features");
            if (featuresObj instanceof Map<?, ?> features) {
                Object fileUpload = features.get("file-upload");
                fileUploadEnabled = Boolean.TRUE.equals(fileUpload);
            }
        }

        return new ProsodyFileSharingPrincipal(userId, displayName, affiliation, meetingId, fileUploadEnabled);
    }

    private boolean hasAudience(Claims claims, String expected) {
        Object aud = claims.get("aud");
        if (aud instanceof String audString) {
            return expected.equals(audString);
        }
        if (aud instanceof Iterable<?> auds) {
            for (Object a : auds) {
                if (expected.equals(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
