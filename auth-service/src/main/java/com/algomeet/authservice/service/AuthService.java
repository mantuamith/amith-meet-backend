package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.exception.UserAlreadyExistsException;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.authservice.enums.ResponseCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    public AuthResponse issueTokensFor(UserResponse user) {
        String accessToken  = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        refreshTokenStore.save(refreshToken, user.getEmail());
        return AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user, accessToken, refreshToken);
    }

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

    public UserResponse registerUser(String username, String email, String password) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        try {
            Map<String,Object> responseMap = userClient.createUser(request);
            // Extract and map "user" object to UserResponse
            return objectMapper.convertValue(responseMap.get("user"), UserResponse.class);
        } catch (FeignException.Conflict e) {
            Set<String> fields = extractDuplicateFields(e);
            String upstreamCode = extractCode(e);
            if (fields == null || fields.isEmpty()) {
                if (ResponseCode.AUTH_DUPLICATE_EMAIL.getCode().equals(upstreamCode)) {
                    fields = Set.of("email");
                } else if (ResponseCode.AUTH_DUPLICATE_USERNAME.getCode().equals(upstreamCode)) {
                    fields = Set.of("username");
                } else if (ResponseCode.AUTH_DUPLICATE_PHONE.getCode().equals(upstreamCode)) {
                    fields = Set.of("phone");
                } else if (ResponseCode.AUTH_DUPLICATE_BOTH.getCode().equals(upstreamCode)) {
                    fields = Set.of("email", "username");
                }
            }
            if (fields == null || fields.isEmpty()) {
                fields = Set.of("unknown");
            }
          ResponseCode code = mapFieldsToResponseCode(fields, upstreamCode);
           String message = buildDuplicateMessage(fields);
            throw new UserAlreadyExistsException(message, fields, code);
        }
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
            if (!passwordEncoder.matches(rawPassword, passwordEncoder.encode(user.getPassword()))) {
                log.warn("LOGIN: bad credentials email={}", maskEmail(email));
                return AuthResponse.from(ResponseCode.AUTH_INVALID_CREDENTIALS, null, null, null);
            }

            // 3) Mint tokens
            String accessToken  = jwtUtil.generateToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // 4) Persist refresh token binding
            refreshTokenStore.save(refreshToken, user.getEmail());


            log.info("LOGIN: success email={}", maskEmail(email));
            
            //After successful registration send "user is online" notification
            Notification notif = new Notification();
            
            // Notify user friends that he/she is online
            notif.setReceiverGroup(ReceiverGroup.USER_FRIENDS);
            // Find user friends list user username
            notif.setReceiverGroupRefId(user.getUsername());
                
            notif.setType(NotificationType.USER_ONLINE);
            notif.setTitle("Send user status");
            notif.setBody("User is online");
            
            // Send the push notification
            notificationService.sendPush(notif);
            
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
    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
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

    private ResponseCode mapFieldsToResponseCode(Set<String> fields, String upstreamCode) {
        // Prefer upstream code if it matches our enum
        if (upstreamCode != null) {
            for (ResponseCode rc : ResponseCode.values()) {
                if (rc.getCode().equals(upstreamCode)) return rc;
            }
        }

        // Otherwise, derive
        boolean email    = fields.contains("email");
        boolean username = fields.contains("username");
        boolean phone    = fields.contains("phone");

        if (email && username && phone) {
            // If you add AUTH_DUPLICATE_ALL, return it here; otherwise reuse BOTH
            return ResponseCode.AUTH_DUPLICATE_BOTH;
        } else if (email && username) {
            return ResponseCode.AUTH_DUPLICATE_BOTH;
        } else if (email) {
            return ResponseCode.AUTH_DUPLICATE_EMAIL;
        } else if (username) {
            return ResponseCode.AUTH_DUPLICATE_USERNAME;
        } else if (phone && hasCode("AUTH_DUPLICATE_PHONE")) {
            return ResponseCode.AUTH_DUPLICATE_PHONE; // add this to enum if not present
        }
        // Fallback
        return ResponseCode.AUTH_DUPLICATE_BOTH;
    }

    private String buildDuplicateMessage(Set<String> fields) {
        boolean email    = fields.contains("email");
        boolean username = fields.contains("username");
        boolean phone    = fields.contains("phone");

        if (email && username && phone) return "Email, username and phone already exist";
        if (email && username)          return "Email and username already exist";
        if (email && phone)             return "Email and phone already exist";
        if (username && phone)          return "Username and phone already exist";
        if (email)                      return "Email already exists";
        if (username)                   return "Username already exists";
        if (phone)                      return "Phone already exists";
        return "Duplicate user fields detected";
    }

    private boolean hasCode(String enumName) {
        try {
            ResponseCode.valueOf(enumName);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

}
