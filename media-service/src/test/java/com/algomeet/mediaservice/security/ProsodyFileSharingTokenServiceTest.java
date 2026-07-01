package com.algomeet.mediaservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.mediaservice.config.ProsodyJwtProperties;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@ExtendWith(MockitoExtension.class)
class ProsodyFileSharingTokenServiceTest {

    @Mock
    private ProsodyPublicKeyLoader publicKeyLoader;

    private ProsodyJwtProperties properties;
    private ProsodyFileSharingTokenService service;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        properties = new ProsodyJwtProperties();
        properties.setAudience("file-sharing");
        properties.setIssuer("prosody");
        properties.setEnabled(true);

        service = new ProsodyFileSharingTokenService(properties, publicKeyLoader);
    }

    private String token(Map<String, Object> overrides) {
        Date now = new Date();
        var builder = Jwts.builder()
                .setAudience("file-sharing")
                .setIssuer("prosody")
                .setNotBefore(new Date(now.getTime() - 60_000))
                .setExpiration(new Date(now.getTime() + 60_000))
                .claim("room", "260701000001")
                .claim("meeting_id", "meeting-abc")
                .claim("context", Map.of(
                        "features", Map.of("file-upload", true),
                        "user", Map.of("id", "user-1", "name", "Hola", "affiliation", "owner")));
        overrides.forEach(builder::claim);
        return builder.signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256).compact();
    }

    @Test
    void parsesValidToken() {
        when(publicKeyLoader.getPublicKey()).thenReturn(keyPair.getPublic());

        ProsodyFileSharingPrincipal principal = service.parseAndValidate(token(Map.of()));

        assertEquals("user-1", principal.userId());
        assertEquals("meeting-abc", principal.meetingId());
        assertTrue(principal.fileUploadFeatureEnabled());
        assertEquals("owner", principal.affiliation());
    }

    @Test
    void fallsBackToRoomWhenMeetingIdMissing() {
        when(publicKeyLoader.getPublicKey()).thenReturn(keyPair.getPublic());

        String token = Jwts.builder()
                .setAudience("file-sharing")
                .setIssuer("prosody")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .claim("room", "260701000001")
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        ProsodyFileSharingPrincipal principal = service.parseAndValidate(token);
        assertEquals("260701000001", principal.meetingId());
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        when(publicKeyLoader.getPublicKey()).thenReturn(keyPair.getPublic());

        String token = Jwts.builder()
                .setAudience("something-else")
                .setIssuer("prosody")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        assertThrows(JwtException.class, () -> service.parseAndValidate(token));
    }

    @Test
    void rejectsWrongIssuer() {
        when(publicKeyLoader.getPublicKey()).thenReturn(keyPair.getPublic());

        String token = Jwts.builder()
                .setAudience("file-sharing")
                .setIssuer("someone-else")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        assertThrows(JwtException.class, () -> service.parseAndValidate(token));
    }

    @Test
    void rejectsExpiredToken() {
        when(publicKeyLoader.getPublicKey()).thenReturn(keyPair.getPublic());

        String token = Jwts.builder()
                .setAudience("file-sharing")
                .setIssuer("prosody")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        assertThrows(JwtException.class, () -> service.parseAndValidate(token));
    }

    @Test
    void rejectsWhenPublicKeyUnavailable() {
        when(publicKeyLoader.getPublicKey()).thenReturn(null);

        assertThrows(JwtException.class, () -> service.parseAndValidate(token(Map.of())));
    }

    @Test
    void rejectsWhenDisabled() {
        properties.setEnabled(false);

        assertThrows(JwtException.class, () -> service.parseAndValidate(token(Map.of())));
    }
}
