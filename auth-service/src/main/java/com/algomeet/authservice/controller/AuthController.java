package com.algomeet.authservice.controller;


import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.LoginPolicy;
import com.algomeet.authservice.enums.LoginResponseType;
import com.algomeet.authservice.dto.AuthResponse;
import com.algomeet.authservice.dto.LoginRequest;
import com.algomeet.authservice.dto.RefreshTokenRequest;
import com.algomeet.authservice.dto.UserResponse;

import com.algomeet.authservice.enums.OtpChannel;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.*;
import com.algomeet.authservice.policy.LoginPolicyEnforcer;
import com.algomeet.authservice.policy.LoginPolicyResolver;
import com.algomeet.authservice.policy.SingleDeviceEnforcer;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.service.NotificationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtUtil jwtUtil;
    private final AuthProperties props;
    private final UserLookupService userLookupService;
    private final OtpService otpService;
    private final LoginPolicyResolver loginPolicyResolver;
    private final RegistrationService registration;
    private final PasswordResetService passwordResetService;
    private final NotificationService notificationService;

    private final UserClient userClient;

    // ---------- Helpers (masking, safe logs) ----------
    private String mLogin(String v){
        return v == null ? "null" : v.replaceAll("^(.{2}).+(@.*)?$","$1***$2");
    }
    private String mDev(String v)
    {
        return v == null ? "null" : (v.length()<=6? "***" : v.substring(0,3)+"***"+v.substring(v.length()-3)); }

    // ----------------- Registration -------------------
    @Deprecated
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        final String username = payload.get("username");
        final String email = payload.get("email");
        // DO NOT LOG raw password
        log.info("REGISTER: attempt username={} email={}", username, mLogin(email));
        try {
            UserResponse user = authService.registerUser(username, email, payload.get("password"));
            log.info("REGISTER: success userId={} username={} email={}", user.getId(), user.getUsername(), mLogin(user.getEmail()));
            return ResponseEntity.ok(AuthResponse.from(ResponseCode.AUTH_REGISTER_SUCCESS, user));
        } catch (Exception e) {
            log.error("REGISTER: failed username={} email={} error={}", username, mLogin(email), e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", ResponseCode.AUTH_REGISTER_FAILED.getCode(),
                    "message", ResponseCode.AUTH_REGISTER_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }

    // ----------------- Token Refresh ------------------
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        // DO NOT LOG refresh token
        log.info("REFRESH: attempt");
        try {
            AuthResponse response = authService.refreshAccessToken(request.getRefreshToken());
            if (ResponseCode.AUTH_INVALID_REFRESH_TOKEN.getCode().equals(response.getCode())) {
                log.warn("REFRESH: invalid refresh token");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            log.info("REFRESH: success for userId={}", response.getUser() != null ? response.getUser().getId() : "unknown");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("REFRESH: failed error={}", e.toString(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", ResponseCode.AUTH_REFRESH_FAILED.getCode(),
                    "message", ResponseCode.AUTH_REFRESH_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }

    // ----------------- Delete Account -----------------
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteAccount(@RequestHeader("Authorization") String authHeader) {
        // DO NOT LOG tokens
        log.info("DELETE: attempt");
        try {
            String token = authHeader.replace("Bearer ", "");
            authService.deleteUser(token);
            log.info("DELETE: success");
            return ResponseEntity.ok(Map.of(
                    "code", ResponseCode.AUTH_USER_DELETED.getCode(),
                    "message", ResponseCode.AUTH_USER_DELETED.getDefaultMessage()
            ));
        } catch (Exception e) {
            log.error("DELETE: failed error={}", e.toString(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "code", ResponseCode.AUTH_DELETE_FAILED.getCode(),
                    "message", ResponseCode.AUTH_DELETE_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }

    // ----------------- Logout -------------------------
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) RefreshTokenRequest request,
                                    @RequestHeader("Authorization") String authHeader,
                                    @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        log.info("LOGOUT: attempt");

        // 0) Defensive default if request is null
        final String refreshToken = (request == null) ? null : request.getRefreshToken();

        try {
            // 1) Extract and validate access token
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "code", ResponseCode.AUTH_LOGOUT_FAILED.getCode(),
                        "message", "Missing or invalid Authorization header"
                ));
            }
            final String accessToken = authHeader.substring(7);
            if (!jwtUtil.isTokenValid(accessToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "code", ResponseCode.AUTH_LOGOUT_FAILED.getCode(),
                        "message", "Invalid access token"
                ));
            }

            // 2) Pull identity + sid from token
            final String email = jwtUtil.extractEmail(accessToken);
            final String sidFromToken = jwtUtil.extractSid(accessToken); // new method you added in Step #2

            // 3) If a refresh token is provided, ensure it belongs to the same user and revoke it
            if (refreshToken != null && !refreshToken.isBlank()) {
                String owner = refreshTokenStore.getEmailForToken(refreshToken);
                if (owner == null || !owner.equals(email)) {
                    log.warn("LOGOUT: refresh token mismatch storedEmail={} tokenEmail={}", mLogin(owner), mLogin(email));
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                            "code", ResponseCode.AUTH_LOGOUT_FAILED.getCode(),
                            "message", "Refresh token does not belong to the authenticated user"
                    ));
                }
                refreshTokenStore.remove(refreshToken); // idempotent
            }

            // 4) Rotate server-side session to invalidate current access token immediately
            //    We need userId for startSession; resolve it once (cheap via user-service)
            UserResponse user = userLookupService.findByLoginOr404(email);

            // Prefer to keep the same deviceId when not provided: fall back to user's active device
            String effectiveDeviceId = (deviceId != null && !deviceId.isBlank())
                    ? deviceId
                    : user.getActiveDeviceId();

            // If we have somewhere to write the new sid, rotate it.
            if (effectiveDeviceId != null) {
                // Generate a fresh sid and push it to user-service, invalidating old tokens by mismatch
                String newSid = java.util.UUID.randomUUID().toString();
                try {
                    // startSession can accept an explicit sid (your user-service supports &sid=)
                    userClient.startSession(user.getId(), effectiveDeviceId, newSid);
                } catch (Exception e) {
                    // If session rotation fails, continue — refresh token is already revoked,
                    // access token will expire naturally. We just won't force immediate invalidation.
                    log.warn("LOGOUT: session rotation failed for userId={} deviceId={} err={}",
                            user.getId(), effectiveDeviceId, e.toString());
                }
            }

            
            // Add push notification
            Notification notif = Notification.builder()
            		.type(NotificationType.USER_OFFLINE)
            		.receiverGroup(ReceiverGroup.USER_FRIENDS)
            		.receiverGroupRefId(user.getUserKey().toString())
            		.title(user.getUsername() + " is offline")
            		.body(user.getUsername() + " is offline")
            		.build();
            notificationService.sendPush(notif);
            
            log.info("LOGOUT: success email={}", mLogin(email));
            return ResponseEntity.ok(Map.of(
                    "code", ResponseCode.AUTH_LOGOUT_SUCCESS.getCode(),
                    "message", ResponseCode.AUTH_LOGOUT_SUCCESS.getDefaultMessage()
            ));

        } catch (Exception e) {
            log.error("LOGOUT: failed error={}", e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "code", ResponseCode.AUTH_LOGOUT_FAILED.getCode(),
                    "message", ResponseCode.AUTH_LOGOUT_FAILED.getDefaultMessage(),
                    "error", e.getMessage()
            ));
        }
    }


    // ----------------- New Flow: INIT -----------------
    @PostMapping("/login/init")
    public ResponseEntity<?> initLogin(@Valid @RequestBody LoginInitRequest request) {
        log.info("LOGIN:init attempt login={} deviceId={} deviceType={}",
                mLogin(request.getLogin()), mDev(request.getDeviceId()), request.getDeviceType());

        final boolean override = Boolean.TRUE.equals(request.getOverrideExisting());

        // 1) Lookup user
        UserResponse user = userLookupService.findByLoginOr404(request.getLogin());

        // 2) Resolve policy
        LoginPolicy policy = loginPolicyResolver.resolve(user);

        // 3) Enforce high-level policy constraints
        try {
            LoginPolicyEnforcer.enforce(policy, request.getDeviceType());
        } catch (Exception ex) {
            log.warn("LOGIN:init policy blocked login={} policy={} deviceType={} reason={}",
                    mLogin(request.getLogin()), policy, request.getDeviceType(), ex.getMessage());
            throw ex;
        }

        AuthResponse auth = authService.validatePassword(request.getLogin(), request.getPassword());
        if (auth == null || !ResponseCode.AUTH_LOGIN_SUCCESS.getCode().equals(auth.getCode())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // 4) Single-device lock (if enabled)
        try {
            if (props.getAuth().isSingleActiveDevice()
                    && user.getActiveDeviceId() != null
                    && !user.getActiveDeviceId().equals(request.getDeviceId())
                    && !override) {
            	
            	// Send push notification
                Notification notif = Notification.builder()
                		.type(NotificationType.LOCKED_SINGLE_DEVICE)
                		.receiverIds(Set.of(user.getUserKey() != null ? user.getUserKey().toString() : user.getUsername()))
                        .title("Account locked " + user.getActiveDeviceId() + " device")
                		.body("Account locked " + user.getActiveDeviceId() + " device")
                		.build();
                notificationService.sendPush(notif);
                
                return ResponseEntity.status(423).body(Map.of(
                        "code", "AUTH_DEVICE_LOCKED",
                        "message", "This account is active on another device.",
                        "activeDeviceId", user.getActiveDeviceId()
                ));
            }
        } catch (Exception ex) {
            log.warn("LOGIN:init single-device blocked login={} activeDeviceId={} incomingDeviceId={} reason={}",
                    mLogin(request.getLogin()), mDev(user.getActiveDeviceId()), mDev(request.getDeviceId()), ex.getMessage());
            throw ex;
        }

        // 5) Branch by policy (direct vs OTP channel)
        switch (policy) {
            case DIRECT: {
                // Centralized: start/rotate session in user-service, revoke (if override), mint JWTs with sid
                AuthResponse finalTokens = authService.issueTokensFor(user, request.getDeviceId(), override);
                
                // Update user used to login device token
               	userClient.updateDeviceTypeAndToken(user.getId(), request.getDeviceType().name(), request.getDeviceToken());
                String refId = (user.getUserKey() != null)
                        ? user.getUserKey().toString()
                        : String.valueOf(user.getUsername());
                // Add push notification
                Notification notif = Notification.builder()
                		.type(NotificationType.USER_ONLINE)
                		.receiverGroup(ReceiverGroup.USER_FRIENDS)
                		.receiverGroupRefId(refId)
                		.title(user.getUsername() + " is online")
                		.body(user.getUsername() + " is online")
                		.build();
                notificationService.sendPush(notif);
                
                return ResponseEntity.ok(finalTokens);
            }
            case EMAIL: {
                String msg = otpService.initEmailLoginOtp(user.getEmail());
                log.info("LOGIN:init OTP dispatched login={} type=email", mLogin(user.getEmail()));
                return ResponseEntity.ok(LoginResponse.emailOtp(msg));
            }
            case PHONE: {
                String msg = otpService.initSmsLoginOtp(request.getLogin());
                log.info("LOGIN:init OTP dispatched login={} type=phone", mLogin(request.getLogin()));
                return ResponseEntity.ok(LoginResponse.phoneOtp(msg));
            }
            case TOTP: {
                // No dispatch; client should prompt for app code
                log.info("LOGIN:init requires TOTP login={}", mLogin(request.getLogin()));
                return ResponseEntity.ok(LoginResponse.totp("Enter your authenticator code"));
            }
            default:
                // Defensive — should never hit because enforce() rejects unknowns.
                throw new IllegalArgumentException("Unsupported login policy: " + policy);
        }
    }


    // ----------------- New Flow: VERIFY ---------------
    @PostMapping("/login/verify")
    public ResponseEntity<AuthResponse> verifyLogin(@Valid @RequestBody LoginVerifyRequest request) {
        log.info("LOGIN:verify attempt login={} type={} deviceId={} deviceType={}",
                mLogin(request.getLogin()), request.getType(), mDev(request.getDeviceId()), request.getDeviceType());

        // 1) Lookup
        UserResponse user = userLookupService.findByLoginOr404(request.getLogin());

        // 2) Policy
        LoginPolicy policy = loginPolicyResolver.resolve(user);

        // 3) Enforce device type constraints
        LoginPolicyEnforcer.enforce(policy, request.getDeviceType());

        // 5) Type match
        LoginResponseType expectedType = switch (policy) {
            case DIRECT -> LoginResponseType.DIRECT;
            case EMAIL  -> LoginResponseType.EMAIL;
            case PHONE  -> LoginResponseType.PHONE;
            case TOTP   -> LoginResponseType.TOTP;
        };
        LoginResponseType providedType = (request.getType().toResponseType());
        if (providedType != expectedType) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Verification type does not match login policy (expected " + expectedType + ")");
        }

        // 6) Verify by policy and mint tokens
        AuthResponse result = null;
        switch (policy) {
            case EMAIL -> {
                if (request.getCode() == null || request.getCode().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is required for EMAIL verification");
                }
                boolean ok = otpService.verifyEmailLoginOtp(user.getEmail(), request.getCode());
                if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP");
                result = authService.issueTokensFor(user, request.getDeviceId(), false);
            }
            case PHONE -> {
                if (request.getCode() == null || request.getCode().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is required for PHONE verification");
                }
                boolean ok = otpService.verifySmsLoginOtp(user.getEmail() /* or user.getPhone() */, request.getCode());
                if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP");
                result = authService.issueTokensFor(user, request.getDeviceId(), false);
            }
            case TOTP -> {
                if (request.getCode() == null || request.getCode().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TOTP is required");
                }
                // TODO: totpService.verify(user, request.getCode());
                result = authService.issueTokensFor(user, request.getDeviceId(), false);
            }
            default -> throw new IllegalArgumentException("Unsupported login policy: " + policy);
        }

        // Update user used to login device token
        userClient.updateDeviceTypeAndToken(user.getId(), request.getDeviceType().name(), request.getDeviceToken());

        // Add push notification
        Notification notif = Notification.builder()
        		.type(NotificationType.USER_ONLINE)
        		.receiverGroup(ReceiverGroup.USER_FRIENDS)
        		.receiverGroupRefId(user.getUserKey().toString())
        		.title(user.getUsername() + " is online")
        		.body(user.getUsername() + " is online")
        		.build();
        notificationService.sendPush(notif);    
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register/init")
    public RegisterInitResponse init(@Valid @RequestBody RegisterInitRequest req,
                                     HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        log.info("REGISTER:init username={} email={} phone={} deviceId={}",
                mask(req.getUsername()), maskEmail(req.getEmail()), maskPhone(req.getPhone()), req.getDeviceId());
        return registration.init(req, ip);
    }

    @PostMapping("/register/verify")
    public RegisterVerifyResponse verify(@Valid @RequestBody RegisterVerifyRequest req,
                                         HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        log.info("REGISTER:verify txn={} deviceId={}", req.getTransactionId(), req.getDeviceId());
        return registration.verify(req, ip);
    }

    @PostMapping("/password/forgot/init")
    public ResponseEntity<Map<String,Object>> forgotPasswordInit(
            @Valid @RequestBody ForgotPasswordInitRequest request) {

        // Resolve user (email/username/phone)
        UserResponse user = userLookupService.findByLoginOr404(request.getLogin());

        // Decide channel: if user has email -> email OTP; else phone -> SMS OTP
        String msg;
        String type;
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            msg = otpService.initEmailResetOtp(user.getEmail());
            type = String.valueOf(OtpChannel.valueOf("EMAIL"));
        } else if (user.getPhone() != null && !user.getPhone().isBlank()) {
            msg = otpService.initSmsResetOtp(user.getPhone());
            type = String.valueOf(OtpChannel.valueOf("PHONE"));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No reachable channel on account");
        }
        log.info("FORGOT:init login={} channel={}", mLogin(request.getLogin()), type);
        return ResponseEntity.ok(Map.of(
                "type", type,
                "message", msg
        ));
    }

    @PostMapping("/password/forgot/verify")
    public ResponseEntity<Map<String,Object>> forgotPasswordVerify(
            @Valid @RequestBody ForgotPasswordVerifyRequest request) {

        // Resolve user
        UserResponse user = userLookupService.findByLoginOr404(request.getLogin());

        // Verify OTP
        String channel;
        boolean ok;
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            ok = otpService.verifyEmailResetOtp(user.getEmail(), request.getCode());
            channel = String.valueOf(OtpChannel.valueOf("EMAIL"));
        } else if (user.getPhone() != null && !user.getPhone().isBlank()) {
            ok = otpService.verifySmsResetOtp(user.getPhone(), request.getCode());
            channel = String.valueOf(OtpChannel.valueOf("PHONE"));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No reachable channel on account");
        }
        if (!ok)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP");

        // Create short-lived reset ticket
        String ticketId = passwordResetService.issueResetTicket(user.getEmail() != null ? user.getEmail()
                        : user.getPhone(),
                channel);

        log.info("FORGOT:verify OK login={} channel={} ticket={}",
                mLogin(request.getLogin()), channel, ticketId.substring(0,8)+"...");

        return ResponseEntity.ok(Map.of(
                "type", channel,
                "message", "OTP verified. You may now reset your password.",
                "ticketId", ticketId
        ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Map<String,Object>> forgotPasswordReset(
            @Valid @RequestBody ForgotPasswordResetRequest request) {

        var ticket = passwordResetService.consumeResetTicketOrThrow(request.getTicketId());

        // Resolve user again from ticket.login
        UserResponse user = userLookupService.findByLoginOr404(ticket.getLogin());

        // Update password
        authService.updatePassword(user.getId(), request.getNewPassword());

        log.info("FORGOT:reset OK login={}", mLogin(ticket.getLogin()));
        return ResponseEntity.ok(Map.of(
                "message", "Password updated successfully."
        ));
    }

    private String mask(String s){ return s==null?"":(s.length()<=2?s:"**"+s.substring(Math.max(0,s.length()-2))); }
    private String maskEmail(String e){ return e==null?"":e.replaceAll("(^.).*(@.*$)","$1***$2"); }
    private String maskPhone(String p){ return p==null?"":p.replaceAll(".(?=.{2})","*"); }
}




