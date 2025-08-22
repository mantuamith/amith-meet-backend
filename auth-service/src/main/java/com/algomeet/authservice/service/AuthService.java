package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.exception.UserAlreadyExistsException;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.authservice.enums.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.algomeet.authservice.util.FeignErrorUtil.extractCode;
import static com.algomeet.authservice.util.FeignErrorUtil.extractDuplicateFields;
@Slf4j
@Service
public class AuthService {

    @Autowired
    private UserClient userClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private ObjectMapper objectMapper; // for mapping user object

    public AuthResponse issueTokensFor(UserResponse user) {
        String accessToken  = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        refreshTokenStore.save(refreshToken, user.getEmail());
        return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, accessToken, refreshToken);
    }

    public UserResponse registerUser(String username, String email, String password) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        try {
            Map<String, Object> responseMap = userClient.createUser(request);

            // Extract and map "user" object to UserResponse
            return objectMapper.convertValue(responseMap.get("user"), UserResponse.class);
        } catch (FeignException.Conflict e) {
            Set<String> fields = extractDuplicateFields(e);

            if (fields.isEmpty()) {
                String code = extractCode(e);
                if (ResponseCode.AUTH_DUPLICATE_EMAIL.getCode().equals(code))
                    fields = Set.of("email");
                else if (ResponseCode.AUTH_DUPLICATE_USERNAME.getCode().equals(code))
                    fields = Set.of("username");
                else if (ResponseCode.AUTH_DUPLICATE_BOTH.getCode().equals(code))
                    fields = Set.of("email", "username");
            }

            if (fields.isEmpty()) fields = Set.of("unknown");
            throw new UserAlreadyExistsException(fields);

        }
    }

    public void bindActiveDevice(Long userId, String deviceId) {
        userClient.updateActiveDevice(userId, deviceId);
    }

    public AuthResponse login(String email, String rawPassword) {
        // 0) Basic sanity
        if (email == null || rawPassword == null || email.isBlank() || rawPassword.isBlank()) {
            log.warn("LOGIN: invalid request (blank email/password)");
            return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
        }

        try {
            // 1) Lookup user. Treat 'not found' as invalid creds (don’t leak which one failed)
            UserResponse user;
            try {
                user = userClient.getUserByEmail(email);
            } catch (feign.FeignException.NotFound nf) {
                log.warn("LOGIN: user not found email={}", maskEmail(email));
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }
            log.info("AuthService: encoder={}", passwordEncoder.getClass().getName());
            log.info("AuthService: raw='{}' len={}", rawPassword, rawPassword == null ? null : rawPassword.length());
            log.info("AuthService: hashLen={} hash='{}'",
                    user.getPassword() == null ? null : user.getPassword().length(),
                    user.getPassword());
            boolean ok = passwordEncoder.matches(rawPassword, user.getPassword());
            log.info("AuthService: matches={}", ok);
            // 2) Verify password
            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                log.warn("LOGIN: bad credentials email={}", maskEmail(email));
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            // 3) Mint tokens
            String accessToken  = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // 4) Persist refresh token binding
            refreshTokenStore.save(refreshToken, user.getEmail());

            log.info("LOGIN: success email={}", maskEmail(email));
            return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, accessToken, refreshToken);

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

    //TODO: automatically log out from all devices by clearing their refresh tokens

    public AuthResponse refreshAccessToken(String refreshToken) {
        if (!jwtUtil.isTokenValid(refreshToken)
                || !jwtUtil.isRefreshToken(refreshToken)
                || !refreshTokenStore.exists(refreshToken)) {
            return AuthResponse.from(ResponseCode.AUTH_INVALID_REFRESH_TOKEN, null, null, null);
        }

        String email = jwtUtil.extractEmail(refreshToken);
        UserResponse user = userClient.getUserByEmail(email);
        String newAccessToken = jwtUtil.generateToken(user);

        return AuthResponse.from(ResponseCode.AUTH_REFRESH_SUCCESS, user, newAccessToken, refreshToken);
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
