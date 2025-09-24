package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // <-- IMPORTANT
class AuthServiceTest {

    @Mock
    UserClient userClient;
    @Mock
    PasswordEncoder encoder;
    @Mock
    JwtUtil jwt;
    @Mock
    RefreshTokenStore rts;
    @Mock
    SidCache sidCache;
    @Mock
    ObjectMapper om;
    @Mock
    NotificationService notifications;

    @InjectMocks AuthService svc;

    @Test
    void validatePassword_success() {
        var user = new UserResponse();
        user.setEmail("a@x.com");
        user.setPassword("$2a$hash");

        when(userClient.getUserByLogin("a@x.com")).thenReturn(user);
        when(encoder.matches("Pw!234567", "$2a$hash")).thenReturn(true);

        var res = svc.validatePassword("a@x.com", "Pw!234567");
        assertThat(res.getCode()).isEqualTo(ResponseCode.AUTH_LOGIN_SUCCESS.getCode());
    }

    @Test
    void validatePassword_notFound_returnsInvalid() {
        var req = feign.Request.create(
                feign.Request.HttpMethod.GET, "/users/login/a@x.com", Map.of(), null, StandardCharsets.UTF_8, null);
        var notFound = new feign.FeignException.NotFound("nf", req, null, null);

        when(userClient.getUserByLogin("no@x.com")).thenThrow(notFound);

        var res = svc.validatePassword("no@x.com", "pw");
        assertThat(res.getCode()).isEqualTo(ResponseCode.AUTH_INVALID_CREDENTIALS.getCode());
    }

    @Test
    void validatePassword_wrongPassword() {
        var user = new UserResponse();
        user.setEmail("a@x.com");
        user.setPassword("$2a$hash");

        when(userClient.getUserByLogin("a@x.com")).thenReturn(user);
        when(encoder.matches("bad", "$2a$hash")).thenReturn(false);

        var res = svc.validatePassword("a@x.com", "bad");
        assertThat(res.getCode()).isEqualTo(ResponseCode.AUTH_INVALID_CREDENTIALS.getCode());
    }

    @Test
    void issueTokensFor_withSidAndOverride() {
        var user = new UserResponse();
        user.setId(1L);
        user.setEmail("a@x.com");

        when(userClient.startSession(1L, "dev-1", null)).thenReturn(Map.of("sid", "S1"));
        when(jwt.generateToken(user, "S1")).thenReturn("acc");
        when(jwt.generateRefreshToken(user, "S1")).thenReturn("ref");

        var out = svc.issueTokensFor(user, "dev-1", true);

        verify(rts).revokeAllForUser("a@x.com");
        verify(rts).save("ref", "a@x.com");
        assertThat(out.getAccessToken()).isEqualTo("acc");
        assertThat(out.getRefreshToken()).isEqualTo("ref");
    }

    @Test
    void refreshAccessToken_success() {
        when(jwt.isTokenValid("R")).thenReturn(true);
        when(jwt.isRefreshToken("R")).thenReturn(true);
        when(rts.exists("R")).thenReturn(true);
        when(jwt.extractEmail("R")).thenReturn("a@x.com");

        var user = new UserResponse();
        user.setEmail("a@x.com");
        when(userClient.getUserByEmail("a@x.com")).thenReturn(user);
        when(jwt.generateToken(user)).thenReturn("NEW");

        var out = svc.refreshAccessToken("R");
        assertThat(out.getCode()).isEqualTo(ResponseCode.AUTH_REFRESH_SUCCESS.getCode());
        assertThat(out.getAccessToken()).isEqualTo("NEW");
    }

    @Test
    void refreshAccessToken_invalid() {
        when(jwt.isTokenValid("bad")).thenReturn(false);

        var out = svc.refreshAccessToken("bad");
        assertThat(out.getCode()).isEqualTo(ResponseCode.AUTH_INVALID_REFRESH_TOKEN.getCode());
    }
}
