package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.RegisterInitRequest;
import com.algomeet.authservice.dto.RegisterInitResponse;
import com.algomeet.authservice.enums.DeviceType;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;


import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.exception.UserAlreadyExistsException;
import feign.FeignException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    @Test
    void init_saves_pending_and_sends_email_otp() {
        // Mocks
        PendingRegistrationRepository pendingRepo = mock(PendingRegistrationRepository.class);
        var passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        OtpService otpService = mock(OtpService.class);
        ObjectMapper mapper = new ObjectMapper();
        UserClient userClient = mock(UserClient.class);

        // Props (TTL used for response/message only)
        AuthProperties props = mock(AuthProperties.class);
        AuthProperties.Otp otpProps = new AuthProperties.Otp();
        otpProps.setTtlSeconds(300);
        when(props.getOtp()).thenReturn(otpProps);

        // userClient says no duplicates
        when(userClient.checkExists("alice@example.com", "alice", null))
                .thenReturn(Map.of("emailTaken", false, "usernameTaken", false, "phoneTaken", false));

        RegistrationService svc = new RegistrationService(
                pendingRepo, passwordEncoder, otpService, mapper, userClient, props
        );

        RegisterInitRequest req = new RegisterInitRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("S3cret!");
        req.setDeviceId("dev-123");
        req.setDeviceType(DeviceType.WEB);

        RegisterInitResponse res = svc.init(req, "127.0.0.1");

        // Assert response
        assertThat(res.getTransactionId()).isNotBlank();
        assertThat(res.getType()).isEqualTo("EMAIL");
        assertThat(res.getMessage()).contains("OTP");

        // pending registration saved
        verify(pendingRepo).save(any());

        // OTP dispatched to email via OtpService
        verify(otpService).initEmailRegistrationOtp("alice@example.com");
        verifyNoMoreInteractions(otpService);
    }

    private RegistrationService svcWith(
            PendingRegistrationRepository pendingRepo,
            org.springframework.security.crypto.password.PasswordEncoder pw,
            OtpService otpService,
            ObjectMapper mapper,
            UserClient userClient,
            AuthProperties props
    ) {
        return new RegistrationService(pendingRepo, pw, otpService, mapper, userClient, props);
    }

    private AuthProperties propsWithOtpTtl(int seconds) {
        AuthProperties props = mock(AuthProperties.class);
        AuthProperties.Otp otpProps = new AuthProperties.Otp();
        otpProps.setTtlSeconds(seconds);
        when(props.getOtp()).thenReturn(otpProps);
        AuthProperties.Auth authProps = new AuthProperties.Auth();
        authProps.setLoginTypePolicyDefault(0);
        when(props.getAuth()).thenReturn(authProps);
        return props;
    }

    @Test
    void init_fails_on_duplicates() {
        var pendingRepo = mock(PendingRegistrationRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var otpService = mock(OtpService.class);
        var mapper = new ObjectMapper();
        var userClient = mock(UserClient.class);
        var props = propsWithOtpTtl(300);

        // email already taken
        when(userClient.checkExists("e@x.com", "alice", null))
                .thenReturn(Map.of("emailTaken", true, "usernameTaken", false, "phoneTaken", false));

        var svc = svcWith(pendingRepo, encoder, otpService, mapper, userClient, props);

        RegisterInitRequest req = new RegisterInitRequest();
        req.setUsername("alice");
        req.setEmail("e@x.com");
        req.setPassword("pw");
        req.setDeviceId("d1");
        req.setDeviceType(DeviceType.WEB);

        assertThatThrownBy(() -> svc.init(req, "127.0.0.1"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("Email already exists");

        verifyNoInteractions(otpService);
        verify(pendingRepo, never()).save(any());
    }

    @Test
    void verify_fails_when_txn_missing() {
        var pendingRepo = mock(PendingRegistrationRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var otpService = mock(OtpService.class);
        var mapper = new ObjectMapper();
        var userClient = mock(UserClient.class);
        var props = propsWithOtpTtl(300);

        when(pendingRepo.findById("bad-txn")).thenReturn(Optional.empty());
        var svc = svcWith(pendingRepo, encoder, otpService, mapper, userClient, props);

        RegisterVerifyRequest req = new RegisterVerifyRequest();
        req.setTransactionId("bad-txn");
        req.setType("EMAIL");
        req.setCode("123456");
        req.setDeviceId("dev-1");
        req.setDeviceType(DeviceType.WEB);

        assertThatThrownBy(() -> svc.verify(req, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired registration txn");
    }

    @Test
    void verify_fails_on_otp_invalid() {
        var pendingRepo = mock(PendingRegistrationRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var otpService = mock(OtpService.class);
        var mapper = new ObjectMapper();
        var userClient = mock(UserClient.class);
        var props = propsWithOtpTtl(300);

        var doc = PendingRegistrationDoc.builder()
                .txn("txn-1")
                .username("alice")
                .email("alice@example.com") // EMAIL path
                .passwordHash("$2a$10$hash")
                .deviceId("dev-1")
                .deviceType(DeviceType.WEB)
                .build();

        when(pendingRepo.findById("txn-1")).thenReturn(Optional.of(doc));
        when(otpService.verifyEmailRegistrationOtp("alice@example.com", "999999")).thenReturn(false);

        var svc = svcWith(pendingRepo, encoder, otpService, mapper, userClient, props);

        RegisterVerifyRequest req = new RegisterVerifyRequest();
        req.setTransactionId("txn-1");
        req.setType("EMAIL");
        req.setCode("999999");
        req.setDeviceId("dev-1");
        req.setDeviceType(DeviceType.WEB);

        assertThatThrownBy(() -> svc.verify(req, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired OTP");

        verify(pendingRepo, never()).deleteById(any());
        verifyNoInteractions(userClient);
    }

    @Test
    void verify_fails_when_user_service_returns_null() {
        var pendingRepo = mock(PendingRegistrationRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var otpService = mock(OtpService.class);
        var mapper = new ObjectMapper();
        var userClient = mock(UserClient.class);
        var props = propsWithOtpTtl(300);

        var doc = PendingRegistrationDoc.builder()
                .txn("txn-1").username("alice").email("alice@example.com")
                .passwordHash("$2a$10$hash").deviceId("dev-1").deviceType(DeviceType.WEB)
                .build();

        when(pendingRepo.findById("txn-1")).thenReturn(Optional.of(doc));
        when(otpService.verifyEmailRegistrationOtp(anyString(), anyString())).thenReturn(true);
        when(userClient.createUser(any(UserRequest.class))).thenReturn(null); // <- null

        var svc = svcWith(pendingRepo, encoder, otpService, mapper, userClient, props);

        var req = new RegisterVerifyRequest();
        req.setTransactionId("txn-1"); req.setType("EMAIL"); req.setCode("123456");
        req.setDeviceId("dev-1"); req.setDeviceType(DeviceType.WEB);

        assertThatThrownBy(() -> svc.verify(req, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("null response");
    }

    @Test
    void verify_fails_when_user_service_returns_error_payload() {
        var pendingRepo = mock(PendingRegistrationRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var otpService = mock(OtpService.class);
        var mapper = new ObjectMapper();
        var userClient = mock(UserClient.class);
        var props = propsWithOtpTtl(300);

        var doc = PendingRegistrationDoc.builder()
                .txn("txn-1").username("alice").email("alice@example.com")
                .passwordHash("$2a$10$hash").deviceId("dev-1").deviceType(DeviceType.WEB)
                .build();

        when(pendingRepo.findById("txn-1")).thenReturn(Optional.of(doc));
        when(otpService.verifyEmailRegistrationOtp(anyString(), anyString())).thenReturn(true);

        // no "user" key -> simulate error payload from user-service
        when(userClient.createUser(any(UserRequest.class)))
                .thenReturn(Map.of("code", "USER_CREATE_FAILED", "message", "duplicate email"));

        var svc = svcWith(pendingRepo, encoder, otpService, mapper, userClient, props);

        var req = new RegisterVerifyRequest();
        req.setTransactionId("txn-1"); req.setType("EMAIL"); req.setCode("123456");
        req.setDeviceId("dev-1"); req.setDeviceType(DeviceType.WEB);

        assertThatThrownBy(() -> svc.verify(req, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate email")
                .hasMessageContaining("USER_CREATE_FAILED");
    }

    @Test
    void verify_fails_on_feign_exception() {
        var pendingRepo = mock(PendingRegistrationRepository.class);
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        var otpService = mock(OtpService.class);
        var mapper = new ObjectMapper();
        var userClient = mock(UserClient.class);
        var props = propsWithOtpTtl(300);

        var doc = PendingRegistrationDoc.builder()
                .txn("txn-1").username("alice").email("alice@example.com")
                .passwordHash("$2a$10$hash").deviceId("dev-1").deviceType(DeviceType.WEB)
                .build();

        when(pendingRepo.findById("txn-1")).thenReturn(Optional.of(doc));
        when(otpService.verifyEmailRegistrationOtp(anyString(), anyString())).thenReturn(true);

        // Simulate Feign 409 with response body
        var ex = FeignException.errorStatus(
                "createUser",
                feign.Response.builder()
                        .status(409)
                        .reason("Conflict")
                        .request(feign.Request.create(feign.Request.HttpMethod.POST, "/users", Map.of(), null, StandardCharsets.UTF_8, null))
                        .body("{\"code\":\"USER_CREATE_FAILED\",\"message\":\"Email exists\"}", StandardCharsets.UTF_8)
                        .build()
        );
        when(userClient.createUser(any(UserRequest.class))).thenThrow(ex);

        var svc = svcWith(pendingRepo, encoder, otpService, mapper, userClient, props);

        var req = new RegisterVerifyRequest();
        req.setTransactionId("txn-1"); req.setType("EMAIL"); req.setCode("123456");
        req.setDeviceId("dev-1"); req.setDeviceType(DeviceType.WEB);

        assertThatThrownBy(() -> svc.verify(req, "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User-service create failed")
                .hasMessageContaining("Email exists");
    }
}
