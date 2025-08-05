package com.algomeet.authservice.controller;

import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.RefreshTokenRequest;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.AuthService;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String email = payload.get("email");
        String password = payload.get("password");

        UserResponse user = authService.registerUser(username, email, password);
        return ResponseEntity.ok(AuthResponse.from(ResponseCode.AUTH_REGISTER_SUCCESS, user));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshAccessToken(request.getRefreshToken());

            if (ResponseCode.AUTH_INVALID_REFRESH_TOKEN.getCode().equals(response.getCode())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", ResponseCode.AUTH_REFRESH_FAILED.getCode(),
                    "message", ResponseCode.AUTH_REFRESH_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }


    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");

        try {
            AuthResponse response = authService.login(email, password);

            if (ResponseCode.AUTH_INVALID_CREDENTIALS.getCode().equals(response.getCode())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", ResponseCode.AUTH_LOGIN_FAILED.getCode(),
                    "message", ResponseCode.AUTH_LOGIN_FAILED.getDefaultMessage()
            ));
        }
    }




    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteAccount(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            authService.deleteUser(token);

            return ResponseEntity.ok(Map.of(
                    "code", ResponseCode.AUTH_USER_DELETED.getCode(),
                    "message", ResponseCode.AUTH_USER_DELETED.getDefaultMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "code", ResponseCode.AUTH_DELETE_FAILED.getCode(),
                    "message", ResponseCode.AUTH_DELETE_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request,
                                    @RequestHeader("Authorization") String authHeader) {
        try {
            String accessToken = authHeader.replace("Bearer ", "");
            String emailFromAccessToken = jwtUtil.extractEmail(accessToken);

            String storedEmail = refreshTokenStore.getEmailForToken(request.getRefreshToken());
            if (storedEmail == null || !storedEmail.equals(emailFromAccessToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "code", ResponseCode.AUTH_LOGOUT_FAILED.getCode(),
                        "message", "Refresh token does not belong to the authenticated user"
                ));
            }

            refreshTokenStore.remove(request.getRefreshToken());

            return ResponseEntity.ok(Map.of(
                    "code", ResponseCode.AUTH_LOGOUT_SUCCESS.getCode(),
                    "message", ResponseCode.AUTH_LOGOUT_SUCCESS.getDefaultMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", ResponseCode.AUTH_LOGOUT_FAILED.getCode(),
                    "message", ResponseCode.AUTH_LOGOUT_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }



}
