package com.algomeet.authservice.controller;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.config.JwtAuthenticationFilter;
import com.algomeet.authservice.config.LocalizationConfig;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.DeviceType;
import com.algomeet.authservice.enums.LoginPolicy;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.otp.OtpRepository;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.algomeet.authservice.policy.LoginPolicyResolver;
import com.algomeet.authservice.service.*;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.support.TestData;
import com.algomeet.authservice.token.RefreshTokenStore;
import com.algomeet.authservice.util.JwtUtil;
import com.algomeet.authservice.util.MessageUtil;
import com.algomeet.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Keep the slice slim; disable data-mongo auto-config and filters
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

@Import(LocalizationConfig.class) // include your config
class AuthControllerLoginDirectTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired MessageSource messageSource;

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
    @MockBean
    UserClient userClient;
    @MockBean
    SidCache sidCache;
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    PendingPasswordResetRepository pendingPasswordResetRepository;
    @MockBean
    PendingRegistrationRepository pendingRegistrationRepository;
    @MockBean
    OtpRepository otpRepository;
    
    @MockBean UserProfileService userProfileService;

	@BeforeEach
	void init() {
		// Initialize messageSource into the MessageUtil constructor
		new MessageUtil(messageSource);
	}

    // ---------- happy path: DIRECT policy issues tokens immediately ----------
    @Test @DisplayName("/auth/login/init — DIRECT policy → tokens issued")
    void login_init_direct_success() throws Exception {
        // request
        LoginInitRequest req = validLoginReq("alice@example.com");

        // user & policy
        UserResponse user = TestData.userResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");

        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        // single-active-device flag off (so we don't hit 423 branch)
        AuthProperties.Auth authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        // valid password -> success code
        when(authService.validatePassword("alice@example.com","CorrectHorse!1"))
               .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user));

        // tokens to return
        AuthResponse tokens = new AuthResponse();
        tokens.setCode(ResponseCode.AUTH_LOGIN_SUCCESS.getCode());
        tokens.setAccessToken("access.jwt");
        tokens.setRefreshToken("refresh.jwt");
        tokens.setUser(user);
        when(authService.issueTokensFor(eq(user), eq("dev-abc"), eq(false)))
               .thenReturn(tokens);

        mvc.perform(post("/auth/login/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value(ResponseCode.AUTH_LOGIN_SUCCESS.getCode()))
           .andExpect(jsonPath("$.accessToken").value("access.jwt"))
           .andExpect(jsonPath("$.refreshToken").value("refresh.jwt"));

        verify(userClient).updateDeviceTypeAndToken(eq(1001L), eq("WEB"), eq("fcm-token-1"));
        verify(notificationService).sendPush(any()); // USER_ONLINE push
    }

    // ---------- single-active-device lock -> 423 ----------
    @Test @DisplayName("/auth/login/init — single-active-device lock → 423")
    void login_init_single_device_locked() throws Exception {
        LoginInitRequest req = validLoginReq("alice@example.com");


        UserResponse user = TestData.userResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        user.setActiveDeviceId("existing-dev");

        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        AuthProperties.Auth authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(true); // lock enabled
        when(props.getAuth()).thenReturn(authCfg);

        when(authService.validatePassword(anyString(), anyString()))
               .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user));

        mvc.perform(post("/auth/login/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
           .andExpect(status().isLocked()) // 423
           .andExpect(jsonPath("$.code").value("AUTH_DEVICE_LOCKED"))
           .andExpect(jsonPath("$.activeDeviceId").value("existing-dev"));

        verify(notificationService).sendPush(any()); // LOCKED_SINGLE_DEVICE push
        verifyNoInteractions(jwtUtil);
    }

    // ---------- bad credentials -> 401 ----------
    @Test @DisplayName("/auth/login/init — invalid credentials → 401")
    void login_init_invalid_credentials() throws Exception {
        LoginInitRequest req = validLoginReq("alice@example.com");


        UserResponse user = new UserResponse();
        user.setEmail("alice@example.com");
        user.setUsername("alice");

        when(userLookupService.findByLoginOr404(anyString())).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        AuthProperties.Auth authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        // simulate failure code from validatePassword
        AuthResponse failed = new AuthResponse();
        failed.setCode(ResponseCode.AUTH_LOGIN_FAILED.getCode());
        when(authService.validatePassword(anyString(), anyString())).thenReturn(failed);

        mvc.perform(post("/auth/login/init")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(req)))
           .andExpect(status().isUnauthorized());
    }

    private LoginInitRequest validLoginReq(String login) {
        LoginInitRequest r = new LoginInitRequest();
        r.setLogin(login);
        r.setPassword("CorrectHorse!1"); // 14 chars
        r.setDeviceId("dev-abc");
        r.setDeviceType(DeviceType.WEB);
        r.setDeviceToken("fcm-token-1");
        r.setOverrideExisting(false);
        return r;
    }

    @Test
    void login_init_direct_wrong_password() throws Exception {
        var req = validLoginReq("alice@example.com");

        var user = new UserResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");

        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        var authCfg = new AuthProperties.Auth();
        authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        // Controller maps this to 401
        when(authService.validatePassword("alice@example.com", "CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_FAILED, null));

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.path").value("/auth/login/init"));

        // No tokens issued, no side effects
        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(notificationService);
        verify(userClient, never()).updateDeviceTypeAndToken(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("/auth/login/init — wrong password → 401 + error payload")
    void login_init_wrong_password_now_401() throws Exception {
        // request
        LoginInitRequest req = validLoginReq("alice@example.com");

        // user & policy
        var user = new UserResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        // single-active-device flag off (not the path we test)
        var authCfg = new AuthProperties.Auth(); authCfg.setSingleActiveDevice(false);
        when(props.getAuth()).thenReturn(authCfg);

        // service signals bad creds → controller throws 401
        when(authService.validatePassword("alice@example.com", "CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_FAILED, null));

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.path").value("/auth/login/init"))
                .andExpect(jsonPath("$.method").value("POST"));

        // Exactly one validatePassword call, and NO token issuance
        verify(authService, times(1)).validatePassword("alice@example.com", "CorrectHorse!1");
        verify(authService, never()).issueTokensFor(any(), anyString(), anyBoolean());

        // Nothing token-related should run
        verifyNoInteractions(jwtUtil);

        // (Optional) ensure we didn't touch device update or notifications on bad creds
        verifyNoInteractions(userClient, notificationService);
    }

    @Test
    @DisplayName("/auth/login/init — single-device ON, overrideExisting=true → allowed")
    void login_init_single_device_override_true_allowsLogin() throws Exception {
        // request with overrideExisting = true
        LoginInitRequest req = new LoginInitRequest();

        TestData.userResponse();
        req.setLogin("alice@example.com");
        req.setPassword("CorrectHorse!1");
        req.setDeviceId("dev-abc");
        req.setDeviceType(DeviceType.WEB);
        req.setDeviceToken("fcm-token-1");

        req.setOverrideExisting(true);

        // user has another active device
        var user = new UserResponse();
        user.setId(1001L);
        user.setEmail("alice@example.com");
        user.setUsername("alice");
        user.setActiveDeviceId("existing-dev");

        when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        when(loginPolicyResolver.resolve(user)).thenReturn(LoginPolicy.DIRECT);

        // single-device policy ON
        var authCfg = new AuthProperties.Auth(); authCfg.setSingleActiveDevice(true);
        when(props.getAuth()).thenReturn(authCfg);

        // password ok
        when(authService.validatePassword("alice@example.com", "CorrectHorse!1"))
                .thenReturn(AuthResponse.from(ResponseCode.AUTH_LOGIN_SUCCESS, user));

        // tokens returned
        var tokens = new AuthResponse();
        tokens.setCode(ResponseCode.AUTH_LOGIN_SUCCESS.getCode());
        tokens.setAccessToken("access.jwt");
        tokens.setRefreshToken("refresh.jwt");
        tokens.setUser(user);
        when(authService.issueTokensFor(eq(user), eq("dev-abc"), eq(true))) // <- override flag true
                .thenReturn(tokens);

        mvc.perform(post("/auth/login/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.AUTH_LOGIN_SUCCESS.getCode()))
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.jwt"));

        verify(userClient).updateDeviceTypeAndToken(1001L, "WEB", "fcm-token-1");
        verify(notificationService).sendPush(any());
    }



}
