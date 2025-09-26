package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.exception.UserAlreadyExistsException;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.authservice.enums.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.algomeet.authservice.util.FeignErrorUtil.extractCode;
import static com.algomeet.authservice.util.FeignErrorUtil.extractDuplicateFields;
@Slf4j
@Service
@AllArgsConstructor
public class AuthService {


    private final UserClient userClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenStore refreshTokenStore;
    private final ObjectMapper objectMapper; // for mapping user object
    private final SidCache sidCache;
    private final NotificationService notificationService;

    public AuthResponse issueTokensFor(UserResponse user, String deviceId, boolean overrideExisting) {
        // 1) Start/rotate server-side session to get sid
        //    (POST /internal/users/{id}/session?deviceId=...)
        Map<String, String> session = userClient.startSession(user.getId(), deviceId, null);
        String sid = session.get("sid");

        // kill cache so next request re-checks immediately
        sidCache.invalidate(user.getEmail());

        // 2) Optionally revoke all existing refresh tokens for this user (single-device override)
        if (overrideExisting) {
            refreshTokenStore.revokeAllForUser(user.getEmail());
        }

        // 3) Mint tokens WITH sid
        String accessToken  = jwtUtil.generateToken(user, sid);
        String refreshToken = jwtUtil.generateRefreshToken(user, sid);

        // 4) Persist refresh token ↔ user binding
        refreshTokenStore.save(refreshToken, user.getEmail());

        return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, accessToken, refreshToken);
    }

    public AuthResponse validatePassword(String email, String rawPassword) {
        // 0) Basic sanity
        if (email == null || rawPassword == null || email.isBlank() || rawPassword.isBlank()) {
            log.warn("LOGIN: invalid request (blank email/password)");
            return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
        }

        try {
            // 1) Lookup user. Treat 'not found' as invalid creds (don’t leak which one failed)
            UserResponse user;
            try {

                String key = normalize(email);

                user = userClient.getUserByLogin(key);
                if (user == null || user.getPassword() == null) {
                    return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
                }
            } catch (feign.FeignException.NotFound nf) {
                log.warn("LOGIN: user not found email={}", maskEmail(email));
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            // 2) Verify password
            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                log.warn("LOGIN: bad credentials email={}", maskEmail(email));
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, null, null);


        } catch (feign.FeignException fe) {
            // External call failed (4xx/5xx). Don’t leak details to client.
            log.error("LOGIN: upstream error for email={}, status={}, msg={}",
                    maskEmail(email), fe.status(), safeMsg(fe));
            return AuthResponse.from(ResponseCode.AUTH_LOGIN_FAILED, null, null, null);

        } catch (Exception e) {
            log.error("LOGIN: unexpected error email={}, err={}", maskEmail(email), e.toString());
            return AuthResponse.from(ResponseCode.AUTH_LOGIN_FAILED, null, null, null);

        }
    }
    public void deleteUser(String token) {
        String email = jwtUtil.extractEmail(token);
        refreshTokenStore.clearAllForEmail(email);//Clear all refresh Tokens
        userClient.deleteUserByEmail(email);
    }
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    //TODO: automatically log out from all devices by clearing their refresh tokens

    public AuthResponse refreshAccessToken(String refreshToken) {
        // 1) structural checks
        if (!jwtUtil.isTokenValid(refreshToken) || !jwtUtil.isRefreshToken(refreshToken)) {
            return AuthResponse.from(ResponseCode.AUTH_INVALID_REFRESH_TOKEN, null);
        }

        // 2) pull identity
        String email = jwtUtil.extractEmail(refreshToken);
        String sidFromRt = jwtUtil.extractSid(refreshToken);
        if (email == null || sidFromRt == null) {
            return AuthResponse.from(ResponseCode.AUTH_INVALID_REFRESH_TOKEN,null);
        }

        // 3) load user
        UserResponse user = userClient.getUserByEmail(email);
        if (user == null) {
            return AuthResponse.from(ResponseCode.AUTH_LOGIN_FAILED, null);
        }

        // 4) compare with server-authoritative SID
        String currentSid = sidCache.getCurrentSid(email); // your existing cache
        if (currentSid == null || !currentSid.equals(sidFromRt)) {
            // either revoked or another login rotated SID
            return AuthResponse.from(ResponseCode.AUTH_SESSION_REVOKED, user);
        }

        // 5) issue new ACCESS token with SAME sid; optionally extend/rotate refresh here
        String newAccess = jwtUtil.generateToken(user, currentSid);
        // You can choose to reuse the same RT (until it nears expiry) or rotate it every time:
        String newRefresh = jwtUtil.generateRefreshToken(user, currentSid);

        return AuthResponse.from(ResponseCode.AUTH_REFRESH_SUCCESS, user, newAccess, newRefresh);
    }

    public void updatePassword(Long userId, String rawPassword) {
        String hash = passwordEncoder.encode(rawPassword); // keep same encoder used elsewhere
        userClient.updatePassword(userId, hash);
    }


    private String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.length() > 200) ? t.getClass().getSimpleName() : m;
    }

}
