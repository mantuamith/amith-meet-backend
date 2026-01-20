package com.algomeet.authservice.controller;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.config.JwtAuthenticationFilter;
import com.algomeet.authservice.config.LocalizationConfig;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.DeviceType;
import com.algomeet.authservice.exception.ResetTicketInvalidException;
import com.algomeet.authservice.otp.OtpRepository;
import com.algomeet.authservice.otp.PendingPasswordResetDoc;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration.class
        }
)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.data.mongodb.repositories.enabled=false",
        "jwt.secret=c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2VjcmV0c2U="
})

@Import(LocalizationConfig.class) // include your config
class AuthControllerForgotPasswordTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    // Controller deps (mocked)
    @MockBean AuthService authService;
    @MockBean RefreshTokenStore refreshTokenStore;
    @MockBean JwtUtil jwtUtil;
    @MockBean AuthProperties props;
    @MockBean UserLookupService userLookupService;
    @MockBean OtpService otpService;
    @MockBean
    LoginPolicyResolver loginPolicyResolver;
    @MockBean RegistrationService registration;
    @MockBean PasswordResetService passwordResetService;
    @MockBean NotificationService notificationService;
    @MockBean UserClient userClient;    
    @MockBean UserProfileService userProfileService;
    @Autowired MessageSource messageSource;
    
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
    
	@BeforeEach
	void init() {
		// Initialize messageSource into the MessageUtil constructor
		new MessageUtil(messageSource);
	}

    // ===== /auth/password/forgot/init (EMAIL path) =====
    @Test
    @DisplayName("/password/forgot/init — email path")
    void forgot_init_email() throws Exception {
        var req = new ForgotPasswordInitRequest();
        req.setLogin("alice@example.com");
        req.setDeviceId("dev-1");
        req.setDeviceType(DeviceType.WEB);

        // user has email => EMAIL OTP path
        var user = TestData.userResponse();

        Mockito.when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        Mockito.when(otpService.initEmailResetOtp("alice@example.com")).thenReturn("OTP sent to your email");

        mvc.perform(post("/auth/password/forgot/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message", containsString("OTP")));

        Mockito.verify(otpService).initEmailResetOtp("alice@example.com");
    }

    // ===== /auth/password/forgot/verify (EMAIL path) =====
    @Test
    @DisplayName("/password/forgot/verify — email path success returns ticketId")
    void forgot_verify_email_success() throws Exception {
        var req = new ForgotPasswordVerifyRequest();
        req.setLogin("alice@example.com");
        req.setCode("123456");
        req.setDeviceId("dev-9");
        req.setDeviceType(DeviceType.WEB);

        var user = TestData.userResponse();

        Mockito.when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);
        Mockito.when(otpService.verifyEmailResetOtp("alice@example.com", "123456")).thenReturn(true);
        Mockito.when(passwordResetService.issueResetTicket(eq("alice@example.com"), eq("EMAIL")))
                .thenReturn("ticket-abc");

        mvc.perform(post("/auth/password/forgot/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EMAIL"))
                .andExpect(jsonPath("$.message", containsString("verified")))
                .andExpect(jsonPath("$.ticketId").value("ticket-abc"));
    }

    // ===== /auth/password/reset (success) =====
    @Test
    @DisplayName("/password/reset — success updates password")
    void forgot_reset_success() throws Exception {
        var req = new ForgotPasswordResetRequest();
        req.setTicketId("ticket-abc");
        req.setNewPassword("NewStrong!1");

        // Return a real PendingPasswordResetDoc
        var ticket = PendingPasswordResetDoc.builder()
                .id("ticket-abc")
                .login("alice@example.com")
                .channel("EMAIL")
                .createdAt(Instant.now())
                .expireAt(Instant.now().plusSeconds(300))
                .build();

        Mockito.when(passwordResetService.consumeResetTicketOrThrow("ticket-abc")).thenReturn(ticket);

        var user = TestData.userResponse();
        Mockito.when(userLookupService.findByLoginOr404("alice@example.com")).thenReturn(user);

        mvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully."));

        Mockito.verify(authService).updatePassword(123L, "NewStrong!1");
    }

    // ===== /auth/password/reset — invalid ticket (400) =====
    @Test
    @DisplayName("/password/reset — invalid ticket -> 400")
    void forgot_reset_invalid_ticket() throws Exception {
        var req = new ForgotPasswordResetRequest();
        req.setTicketId("bad-ticket");
        req.setNewPassword("NewStrong!1");

        Mockito.when(passwordResetService.consumeResetTicketOrThrow("bad-ticket"))
                .thenThrow(new ResetTicketInvalidException("Invalid or expired password reset ticket"));

        mvc.perform(post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
