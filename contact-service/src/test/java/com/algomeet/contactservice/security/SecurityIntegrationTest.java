package com.algomeet.contactservice.security;

import com.algomeet.contactservice.controller.ContactController;
import com.algomeet.contactservice.i18n.MessageResolver;
import com.algomeet.contactservice.service.ContactService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end through the SecurityFilterChain with real JWT verification.
 * We mock the ContactService to avoid DB calls; the goal is to verify:
 *  - 200 for a valid token (with user_key claim)
 *  - 401 for an expired token
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 32-byte (256-bit) key, Base64 encoded
                // (generate with: new String(Base64.getEncoder().encode(SecureRandom bytes)))
                "jwt.secret=K2lB2g0pV4yVvYV0a07P0qG5g8nX6z3q2gJm9y6Qp4w=", // example only
                // keep app lightweight for this slice
                "spring.flyway.enabled=false",
                "spring.liquibase.enabled=false"
        }
)
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired private MockMvc mvc;

    // Mock downstream to keep test focused on security plumbing
    @MockBean private ContactService contactService;
    @MockBean private MessageResolver i18n;

    private SecretKey key;

    @BeforeEach
    void setup() {
        // mirror application JwtAuthenticationFilter (Base64 decode -> HMAC key)
        String secret = "K2lB2g0pV4yVvYV0a07P0qG5g8nX6z3q2gJm9y6Qp4w=";
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

        // Minimal stubs for controller’s i18n and service
        when(i18n.msg("success.contact.list")).thenReturn("success.contact.list");
        when(i18n.msg(any(com.algomeet.contactservice.enums.ResponseCode.class)))
                .thenAnswer(inv -> inv.getArgument(0).toString());
        when(contactService.getContactList(any())).thenReturn(List.of());
    }

    private String jwt(Map<String, Object> claims, Instant exp) {
        var now = new Date();
        return Jwts.builder()
                .setSubject((String) claims.getOrDefault("sub", "user@example.com"))
                .addClaims(claims)
                .setIssuedAt(now)
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void securedEndpoint_withValidJwt_returns200() throws Exception {
        String userKey = UUID.randomUUID().toString();
        String token = jwt(
                Map.of(
                        "sub", "user@example.com",
                        "username", "user@example.com",
                        "user_key", userKey,
                        "sid", UUID.randomUUID().toString()
                ),
                Instant.now().plusSeconds(600)
        );

        mvc.perform(get("/api/contacts")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentType(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.code").value("OK"))
           .andExpect(jsonPath("$.message").value("success.contact.list"))
           .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void securedEndpoint_withExpiredJwt_returns401() throws Exception {
        String token = jwt(
                Map.of(
                        "sub", "user@example.com",
                        "username", "user@example.com",
                        "user_key", UUID.randomUUID().toString(),
                        "sid", UUID.randomUUID().toString()
                ),
                Instant.now().minusSeconds(10) // already expired
        );

        // Your JwtAuthenticationFilter sends an error (401) directly for expired tokens.
        mvc.perform(get("/api/contacts")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON))
           .andExpect(status().isUnauthorized());
        // Body may be framework default because filter uses sendError; asserting status is enough here.
    }
}
