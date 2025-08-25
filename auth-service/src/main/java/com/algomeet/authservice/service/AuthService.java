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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.algomeet.authservice.util.FeignErrorUtil.extractCode;
import static com.algomeet.authservice.util.FeignErrorUtil.extractDuplicateFields;

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

    public AuthResponse login(String login, String rawPassword) {
        try {
            String key = normalize(login);

            UserResponse user = userClient.getUserByLogin(key);
            if (user == null || user.getPassword() == null) {
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            String accessToken  = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            refreshTokenStore.save(refreshToken, user.getEmail()); // or user.getId()

            return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, accessToken, refreshToken);

        } catch (FeignException.NotFound e) {
            return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
        } catch (Exception e) {
            // log appropriately
            return AuthResponse.from(ResponseCode.AUTH_LOGIN_ERROR, null, null, null);
        }
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
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

}
