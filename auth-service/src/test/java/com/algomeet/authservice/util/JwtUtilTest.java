package com.algomeet.authservice.util;

import com.algomeet.authservice.dto.UserResponse;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwt;

    // Base64 for a 32+ byte HMAC key (HS256)
    private static final String TEST_SECRET_B64 =
            "c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U=";

    private static UserResponse user(String email, String username, String role) {
        UserResponse u = new UserResponse();
        u.setId(101L);
        u.setEmail(email);
        u.setUsername(username);
        u.setRole(role);
        u.setUserKey(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        u.setTenantId(7);
        return u;
    }

    @BeforeEach
    void setUp() {
        // Directly call the constructor with the base64 secret
        jwt = new JwtUtil(TEST_SECRET_B64);
    }

    @Test
    void generateToken_and_parse_claims() {
        var u = user("alice@example.com", "alice", "USER");

        String token = jwt.generateToken(u);
        assertThat(token).isNotBlank();

        Claims claims = jwt.extractClaim(token, c -> c); // full claims
        assertThat(claims.getSubject()).isEqualTo("alice@example.com");
        assertThat(claims.get("username", String.class)).isEqualTo("alice");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("tenantId", Integer.class)).isEqualTo(7);
        assertThat(jwt.extractUserKey(token)).isEqualTo("11111111-2222-3333-4444-555555555555");

        // convenience extractors
        assertThat(jwt.extractEmail(token)).isEqualTo("alice@example.com");
        assertThat(jwt.extractRole(token)).isEqualTo("USER");
        assertThat(jwt.isTokenValid(token)).isTrue();
    }

    @Test
    void generateToken_with_sid_and_extractSid() {
        var u = user("bob@example.com", "bob", "ADMIN");
        String token = jwt.generateToken(u, "SID123");
        assertThat(token).isNotBlank();

        assertThat(jwt.extractSid(token)).isEqualTo("SID123");
        assertThat(jwt.extractEmail(token)).isEqualTo("bob@example.com");
        assertThat(jwt.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwt.isTokenValid(token)).isTrue();
    }

    @Test
    void refreshToken_and_detection() {
        var u = user("carol@example.com", "carol", "USER");

        String r1 = jwt.generateRefreshToken(u);
        String r2 = jwt.generateRefreshToken(u, "REF-SID-1");

        assertThat(r1).isNotBlank();
        assertThat(r2).isNotBlank();

        // both must be recognized as refresh tokens
        assertThat(jwt.isRefreshToken(r1)).isTrue();
        assertThat(jwt.isRefreshToken(r2)).isTrue();

        // Not a refresh token
        String access = jwt.generateToken(u);
        assertThat(jwt.isRefreshToken(access)).isFalse();
    }

    @Test
    void isTokenValid_false_for_tampered_payload() {
        UserResponse u = new UserResponse();
        u.setEmail("alice@example.com");
        u.setUsername("alice");
        u.setRole("USER");

        String token = jwt.generateToken(u);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
        payload[0] ^= 0x01; // flip one bit
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload);

        String tampered = String.join(".", parts);

        assertThat(jwt.isTokenValid(tampered)).isFalse();
    }
    private static final String TEST_SECRET = "0123456789_0123456789_0123456789_01";
    @Test
    void isTokenValid_false_whenExpired() {
        String expired = JwtTestFactory.expiredTokenFor("alice@example.com",TEST_SECRET); // helper you already have
        assertThat(jwt.isTokenValid(expired)).isFalse();
    }
}
