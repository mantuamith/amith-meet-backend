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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

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
        } catch (FeignException.Conflict ex) {
            throw new UserAlreadyExistsException("User with this email already exists.");
        }
    }

    public AuthResponse login(String email, String rawPassword) {
        try {
            UserResponse user = userClient.getUserByEmail(email);
            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                // return error response instead of throwing
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            String accessToken = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            refreshTokenStore.save(refreshToken, user.getEmail());

            return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, accessToken, refreshToken);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Login failed: " + e.getMessage());  // You can replace with a custom AuthException
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

}
