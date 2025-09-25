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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        // Keep Mongo out of a web-slice
        excludeAutoConfiguration = {
                MongoAutoConfiguration.class,
                MongoDataAutoConfiguration.class,
                MongoRepositoriesAutoConfiguration.class
        },
        // Also ignore any @EnableMongoRepositories found on your main app
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = EnableMongoRepositories.class
        )
)
@AutoConfigureMockMvc(addFilters = false) // don't run security filters here
@TestPropertySource(properties = {
        "spring.data.mongodb.repositories.enabled=false",
        "jwt.secret=c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U="
})
class AuthControllerRegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    // === Required collaborators of AuthController (mock all)
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

    // Shield Mongo repos if anything still tries to create them
    @MockBean com.algomeet.authservice.otp.OtpRepository otpRepository;
    @MockBean com.algomeet.authservice.otp.PendingPasswordResetRepository pendingPasswordResetRepository;
    @MockBean com.algomeet.authservice.otp.PendingRegistrationRepository pendingRegistrationRepository;

    // ---------- /auth/register/init ----------
    @Test @DisplayName("/auth/register/init — success (EMAIL path)")
    void register_init_success_email() throws Exception {
        RegisterInitRequest req = new RegisterInitRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("S3cret!");
        req.setDeviceId("dev-123");
        req.setDeviceType(DeviceType.WEB);

        RegisterInitResponse mockRes = new RegisterInitResponse("txn-001", "EMAIL", "OTP sent to email");
        when(registration.init(any(RegisterInitRequest.class), anyString()))
                .thenReturn(mockRes);

        mvc.perform(post("/auth/register/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.transactionId").value("txn-001"))
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OTP")));

        // verify we forwarded request + captured IP string
        ArgumentCaptor<RegisterInitRequest> cap = ArgumentCaptor.forClass(RegisterInitRequest.class);
        verify(registration).init(cap.capture(), anyString());
        RegisterInitRequest seen = cap.getValue();
        assertThat(seen.getUsername()).isEqualTo("alice");
        assertThat(seen.getEmail()).isEqualTo("alice@example.com");
        assertThat(seen.getDeviceId()).isEqualTo("dev-123");
        assertThat(seen.getDeviceType()).isEqualTo(DeviceType.WEB);
    }

    @Test @DisplayName("/auth/register/init — 400 when neither email nor phone provided")
    void register_init_validation_fails_no_contact() throws Exception {
        String badJson = "{"
                + "\"username\":\"alice\","
                + "\"password\":\"S3cret!\","
                + "\"deviceId\":\"dev-123\","
                + "\"deviceType\":\"WEB\""
                + "}";

        mvc.perform(post("/auth/register/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }

    // ---------- /auth/register/verify ----------
    @Test @DisplayName("/auth/register/verify — success")
    void register_verify_success() throws Exception {
        RegisterVerifyRequest req = new RegisterVerifyRequest();
        req.setTransactionId("txn-001");
        req.setType("EMAIL");
        req.setCode("123456");
        req.setDeviceId("dev-123");
        req.setDeviceType(DeviceType.WEB);

        UserResponse user = new UserResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");

        RegisterVerifyResponse mockRes = new RegisterVerifyResponse();
        mockRes.setType("EMAIL");
        mockRes.setMessage("Registration complete");
        mockRes.setUser(user);

        when(registration.verify(any(RegisterVerifyRequest.class), anyString()))
                .thenReturn(mockRes);

        mvc.perform(post("/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                // match RegisterVerifyResponse fields (no wrapper)
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Registration")))
                .andExpect(jsonPath("$.user.id").value(1001));

        verify(registration).verify(any(RegisterVerifyRequest.class), anyString());
    }

    @Test @DisplayName("/auth/register/verify — 400 on missing fields")
    void register_verify_bad_request() throws Exception {
        String badJson = "{"
                + "\"transactionId\":\"txn-001\","
                + "\"type\":\"EMAIL\""
                + "}";

        mvc.perform(post("/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("/auth/password/forgot/init — sends email OTP to resolved user email")
    void forgot_init_sends_email_otp() throws Exception {
        // arrange
        var user = new UserResponse();
        user.setEmail("alice@example.com");
        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(otpService.initEmailResetOtp("alice@example.com")).thenReturn("OTP sent to email");

        String json = """
      {"login":"alice@example.com"}
    """;

        // act + assert
        mvc.perform(post("/auth/password/forgot/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OTP")));

        // verify OTP dispatch to specific email
        verify(otpService).initEmailResetOtp("alice@example.com");
        Mockito.verifyNoMoreInteractions(otpService);
    }

    @Test
    void login_init_totp_policy_prompts_without_otp_send() throws Exception {
        // request that passes validation
        var req = new LoginInitRequest();
        req.setLogin("alice@example.com");
        req.setPassword("CorrectHorse!1");     // >= 8 chars
        req.setDeviceId("dev-123");            // non-blank
        req.setDeviceType(DeviceType.ANDROID); // TOTP allowed on mobile

        // stubs match the request
        var user = new UserResponse();
        user.setId(1L);
        user.setEmail("AlicE@example.com");

        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.TOTP);

        var authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        when(authService.validatePassword("alice@example.com", "CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, null));

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TOTP"))
        // Optionally assert message if your controller sets it consistently:
         .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Enter")));

        verifyNoInteractions(otpService, jwtUtil); // no OTP dispatch, no token mint
        verify(authService).validatePassword("alice@example.com", "CorrectHorse!1");
    }

    @Test
    void login_init_direct_policy_returns_jwt() throws Exception {
        var req = new LoginInitRequest();
        req.setLogin("alice@example.com");
        req.setPassword("CorrectHorse!1");
        req.setDeviceId("dev-1");
        req.setDeviceType(DeviceType.WEB);
        // overrideExisting is nullable → default false

        // Controller collaborators
        var user = new UserResponse();
        user.setId(123L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");

        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        var authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(false); // avoid the 423 path
        when(props.getAuth()).thenReturn(authCfg);

        // Service calls inside the controller for DIRECT
        when(authService.validatePassword("alice@example.com", "CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, null, null, null));

        var out = new AuthResponse();
        out.setCode(ResponseCode.AUTH_LOGIN_SUCCESS.getCode());
        out.setMessage("OK");
        out.setAccessToken("access.jwt");
        out.setRefreshToken("refresh.jwt");

        when(authService.issueTokensFor(any(UserResponse.class), eq("dev-1"), eq(false)))
                .thenReturn(out);

        // Execute + verify
        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.jwt"));

        // DIRECT path must not use OTP
        verifyNoInteractions(otpService);
    }

    @Test
    void login_init_email_policy_sends_email_otp() throws Exception {
        var req = new LoginInitRequest();
        req.setLogin("alice@example.com");
        req.setPassword("CorrectHorse!1");
        req.setDeviceId("dev-1");
        req.setDeviceType(DeviceType.WEB);

        var user = new UserResponse(); user.setId(123L); user.setEmail("alice@example.com");
        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.EMAIL);

        var authCfg = new AuthProperties.Auth(); authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        when(authService.validatePassword("alice@example.com","CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, null));
        when(otpService.initEmailLoginOtp("alice@example.com")).thenReturn("OTP sent");

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OTP")));

        verify(otpService).initEmailLoginOtp("alice@example.com");
        verifyNoMoreInteractions(otpService);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void login_init_phone_policy_sends_sms_otp() throws Exception {
        var req = new LoginInitRequest();
        req.setLogin("15551234");
        req.setPassword("CorrectHorse!1");
        req.setDeviceId("dev-1");
        req.setDeviceType(DeviceType.ANDROID);

        var user = new UserResponse(); user.setId(123L); user.setPhone("15551234");
        when(userLookupService.findByLoginOr404("15551234")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.PHONE);

        var authCfg = new AuthProperties.Auth(); authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        when(authService.validatePassword("15551234","CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, null));
        when(otpService.initSmsLoginOtp("15551234")).thenReturn("OTP sent");

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PHONE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("OTP")));

        verify(otpService).initSmsLoginOtp("15551234");
        verifyNoMoreInteractions(otpService);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void login_init_missing_login_400() throws Exception {
        var bad = """
      {"password":"CorrectHorse!1","deviceId":"dev-1","deviceType":"WEB"}
    """;
        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }




}
