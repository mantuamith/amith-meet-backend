package com.algomeet.authservice.controller;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.DeviceType;
import com.algomeet.authservice.enums.LoginPolicy;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.policy.LoginPolicyResolver;
import com.algomeet.authservice.service.*;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

// keep security filters off; we’re unit testing controller layer
@WebMvcTest(controllers = AuthController.class)
@TestPropertySource(properties = {
        "spring.data.mongodb.repositories.enabled=false",
        "jwt.secret=c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U="
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class AuthControllerLoginEmailOtpTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    // controller deps
    @MockBean AuthService authService;
    @MockBean RefreshTokenStore refreshTokenStore;
    @MockBean JwtUtil jwtUtil;
    @MockBean AuthProperties props;
    @MockBean UserLookupService userLookupService;
    @MockBean OtpService otpService;
    @MockBean LoginPolicyResolver loginPolicyResolver;
    @MockBean RegistrationService registration;
    @MockBean PasswordResetService passwordResetService;
    @MockBean NotificationService notificationService;
    @MockBean UserClient userClient;
    @MockBean com.algomeet.authservice.session.SidCache sidCache;
    @MockBean com.algomeet.authservice.config.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean com.algomeet.authservice.otp.PendingPasswordResetRepository pendingPasswordResetRepository;
    @MockBean com.algomeet.authservice.otp.PendingRegistrationRepository pendingRegistrationRepository;
    @MockBean com.algomeet.authservice.otp.OtpRepository otpRepository;

    // ===== /auth/login/init (EMAIL policy) =====
    @Test
    @DisplayName("/auth/login/init — EMAIL policy → sends OTP")
    void login_init_email_sendsOtp() throws Exception {
        // request body
        LoginInitRequest req = new LoginInitRequest();
        req.setLogin("alice@example.com");
        req.setPassword("Correct!1");  // >= 8 chars
        req.setDeviceId("dev-otp-1");
        req.setDeviceType(DeviceType.WEB);
        req.setDeviceToken("fcm-token-xyz");
        req.setOverrideExisting(false);

        // user + policy
        UserResponse user = new UserResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");

        Mockito.when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        Mockito.when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.EMAIL);

        // single-device not relevant here; still provide props
        AuthProperties.Auth authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(false);
        Mockito.when(props.getAuth()).thenReturn(authCfg);

        // password must be valid before OTP is sent
        Mockito.when(authService.validatePassword("alice@example.com", "Correct!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user));

        Mockito.when(otpService.initEmailLoginOtp("alice@example.com"))
                .thenReturn("OTP sent to your email");

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message", containsString("OTP")));

        // no tokens yet (that happens in /verify)
        Mockito.verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("/auth/login/init — EMAIL policy with bad credentials → 401")
    void login_init_email_badPassword() throws Exception {
        LoginInitRequest req = new LoginInitRequest();
        req.setLogin("alice@example.com");
        req.setPassword("WrongPass!1"); // still >= 8, but we’ll return fail
        req.setDeviceId("d1");
        req.setDeviceType(DeviceType.WEB);

        UserResponse user = new UserResponse();
        user.setEmail("alice@example.com");
        user.setUsername("alice");

        Mockito.when(userLookupService.findByLoginOr404(anyString())).thenReturn(user);
        Mockito.when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.EMAIL);

        AuthResponse fail = new AuthResponse();
        fail.setCode(ResponseCode.AUTH_LOGIN_FAILED.getCode());
        Mockito.when(authService.validatePassword(anyString(), anyString())).thenReturn(fail);

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ===== /auth/login/verify (EMAIL policy) =====
    @Test
    @DisplayName("/auth/login/verify — EMAIL policy → OTP OK → tokens")
    void login_verify_email_success() throws Exception {
        // Request body (password here is harmless; the verify DTO ignores it)
        String body = """
    {
      "login": "alice@example.com",
      "type": "EMAIL",
      "code": "123456",
      "deviceId": "dev-otp-1",
      "deviceType": "WEB",
      "deviceToken": "fcm-token-xyz",
      "password": "Correct!1"
    }
    """;

        // --- Arrange
        UserResponse user = com.algomeet.authservice.support.TestData.userResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        // If UserResponse.userKey is a String, keep this line instead:
        //user.setUserKey(java.util.UUID.randomUUID());

        when(userLookupService.findByLoginOr404("alice@example.com"))
                .thenReturn(user);

        // Don’t couple the stub to the exact instance — any(UserResponse) is safer
        when(loginPolicyResolver.resolve(any(UserResponse.class)))
                .thenReturn(LoginPolicy.EMAIL);

        when(otpService.verifyEmailLoginOtp("alice@example.com", "123456"))
                .thenReturn(true);

        AuthResponse tokens = new AuthResponse();
        tokens.setCode(ResponseCode.AUTH_LOGIN_SUCCESS.getCode());
        tokens.setAccessToken("a.jwt");
        tokens.setRefreshToken("r.jwt");
        tokens.setUser(user);

        when(authService.issueTokensFor(any(UserResponse.class), eq("dev-otp-1"), eq(false)))
                .thenReturn(tokens);

        // --- Act + Assert
        mvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ResponseCode.AUTH_LOGIN_SUCCESS.getCode()))
                .andExpect(jsonPath("$.accessToken").value("a.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("r.jwt"));

        // --- Verify side-effects
        verify(userClient).updateDeviceTypeAndToken(1001L, "WEB", "fcm-token-xyz");
        verify(notificationService).sendPush(any()); // USER_ONLINE push
    }


    @Test
    @DisplayName("/auth/login/verify — wrong 'type' for EMAIL policy → 400")
    void login_verify_wrong_type() throws Exception {
        String body = """
        {
          "login": "alice@example.com",
          "type": "PHONE",
          "code": "123456",
          "deviceId": "dev-otp-1",
          "deviceType": "WEB",
          "password": "Correct!1"
        }
        """;

        UserResponse user = new UserResponse();
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        Mockito.when(userLookupService.findByLoginOr404(anyString())).thenReturn(user);
        Mockito.when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.EMAIL);

        mvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Verification type does not match")));
    }

    @Test
    @DisplayName("/auth/login/verify — EMAIL policy → OTP invalid/expired → 401")
    void login_verify_email_badOtp() throws Exception {
        String body = """
        {
          "login": "alice@example.com",
          "type": "EMAIL",
          "code": "000000",
          "deviceId": "dev-otp-1",
          "deviceType": "WEB",
          "password": "Correct!1"
        }
        """;

        UserResponse user = new UserResponse();
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        Mockito.when(userLookupService.findByLoginOr404(anyString())).thenReturn(user);
        Mockito.when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.EMAIL);

        Mockito.when(otpService.verifyEmailLoginOtp("alice@example.com", "000000")).thenReturn(false);

        mvc.perform(post("/auth/login/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Invalid or expired OTP")));
    }
}
