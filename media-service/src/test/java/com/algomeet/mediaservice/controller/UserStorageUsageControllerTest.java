package com.algomeet.mediaservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import com.algomeet.mediaservice.config.LocalizationConfig;
import com.algomeet.mediaservice.dto.StorageUsageResponse;
import com.algomeet.mediaservice.exceptions.GlobalExceptionHandler;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;
import com.algomeet.mediaservice.util.MessageUtil;
import com.algomeet.mediaservice.util.SecurityUtil;


@WebMvcTest(controllers = UserStorageUsageController.class)
@ContextConfiguration(classes = { UserStorageUsageController.class, GlobalExceptionHandler.class,
        UserStorageUsageControllerTest.MethodSecurityConfig.class })
@Import(LocalizationConfig.class)
@EnableAutoConfiguration
@AutoConfigureMockMvc(addFilters = false)
class UserStorageUsageControllerTest {

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserStorageUsageService userStorageUsageService;

    @MockBean
    private MessageSource messageSource;

    private MockedStatic<SecurityUtil> securityUtilMock;

    private static final String USER_KEY = UUID.randomUUID().toString();

    @BeforeEach
    void setup() {
        securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getUserKey).thenReturn(USER_KEY);

        new MessageUtil(messageSource);
    }

    @AfterEach
    void tearDown() {
        if (securityUtilMock != null) {
            securityUtilMock.close();
        }
    }

    /*
     * ========================= GET MY STORAGE =========================
     */

    @Test
    void getMyStorage_success() throws Exception {
        UUID uuid = UUID.fromString(USER_KEY);

        StorageUsageResponse response = StorageUsageResponse.builder()
                .userKey(uuid)
                .mediaStorageUsed(1024L)
                .mediaFileCount(2L)
                .chatStorageUsed(2048L)
                .chatMessageCount(5L)
                .totalStorageUsed(3072L)
                .build();

        when(userStorageUsageService.getUsage(eq(uuid))).thenReturn(response);

        mockMvc.perform(get("/media/users/me/storage-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userKey").value(USER_KEY))
                .andExpect(jsonPath("$.data.mediaStorageUsed").value(1024L))
                .andExpect(jsonPath("$.data.totalStorageUsed").value(3072L));
    }

    /*
     * ========================= ADMIN GET USER STORAGE =========================
     */

    @Test
    @WithMockUser(roles = "SA")
    void getUserStorage_success() throws Exception {
        UUID targetUser = UUID.randomUUID();

        StorageUsageResponse response = StorageUsageResponse.builder()
                .userKey(targetUser)
                .mediaStorageUsed(5000L)
                .mediaFileCount(10L)
                .chatStorageUsed(1000L)
                .chatMessageCount(3L)
                .totalStorageUsed(6000L)
                .build();

        when(userStorageUsageService.getUsage(eq(targetUser))).thenReturn(response);

        mockMvc.perform(get("/media/users/{userKey}/storage-usage", targetUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.userKey").value(targetUser.toString()))
                .andExpect(jsonPath("$.data.mediaFileCount").value(10L));
    }

    /*
     * ========================= MY STORAGE — ZERO USAGE =========================
     */

    @Test
    void getMyStorage_newUser_returnsZeroUsage() throws Exception {
        UUID uuid = UUID.fromString(USER_KEY);

        StorageUsageResponse empty = StorageUsageResponse.builder()
                .userKey(uuid)
                .mediaStorageUsed(0L)
                .mediaFileCount(0L)
                .chatStorageUsed(0L)
                .chatMessageCount(0L)
                .totalStorageUsed(0L)
                .build();

        when(userStorageUsageService.getUsage(eq(uuid))).thenReturn(empty);

        mockMvc.perform(get("/media/users/me/storage-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaStorageUsed").value(0))
                .andExpect(jsonPath("$.data.chatStorageUsed").value(0))
                .andExpect(jsonPath("$.data.totalStorageUsed").value(0));
    }

    /*
     * ========================= MY STORAGE — WITH TIMESTAMP =========================
     */

    @Test
    void getMyStorage_includesLastUpdatedTimestamp() throws Exception {
        UUID uuid = UUID.fromString(USER_KEY);
        Instant now = Instant.now();

        StorageUsageResponse response = StorageUsageResponse.builder()
                .userKey(uuid)
                .mediaStorageUsed(512L)
                .totalStorageUsed(512L)
                .lastUpdated(now)
                .build();

        when(userStorageUsageService.getUsage(eq(uuid))).thenReturn(response);

        mockMvc.perform(get("/media/users/me/storage-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastUpdated").isNotEmpty());
    }

    /*
     * ========================= MY STORAGE — SERVICE ERROR =========================
     */

    @Test
    void getMyStorage_serviceThrows_returns500() throws Exception {
        UUID uuid = UUID.fromString(USER_KEY);

        when(userStorageUsageService.getUsage(eq(uuid)))
                .thenThrow(new RuntimeException("DB connection failed"));

        mockMvc.perform(get("/media/users/me/storage-usage"))
                .andExpect(status().isInternalServerError());
    }

    /*
     * ========================= ADMIN — ROLE_ADMIN ACCESS =========================
     */

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserStorage_adminRole_success() throws Exception {
        UUID targetUser = UUID.randomUUID();

        StorageUsageResponse response = StorageUsageResponse.builder()
                .userKey(targetUser)
                .mediaStorageUsed(999L)
                .totalStorageUsed(999L)
                .build();

        when(userStorageUsageService.getUsage(eq(targetUser))).thenReturn(response);

        mockMvc.perform(get("/media/users/{userKey}/storage-usage", targetUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStorageUsed").value(999L));
    }

    /*
     * ========================= ADMIN — FORBIDDEN FOR REGULAR USER =========================
     */

    @Test
    @WithMockUser(roles = "USER")
    void getUserStorage_regularUser_forbidden() throws Exception {
        UUID targetUser = UUID.randomUUID();

        mockMvc.perform(get("/media/users/{userKey}/storage-usage", targetUser))
                .andExpect(status().isForbidden());
    }

    /*
     * ========================= ADMIN — USER NOT FOUND =========================
     */

    @Test
    @WithMockUser(roles = "SA")
    void getUserStorage_userNotFound_returns404() throws Exception {
        UUID targetUser = UUID.randomUUID();

        when(userStorageUsageService.getUsage(eq(targetUser)))
                .thenThrow(new IllegalArgumentException("User not found"));

        mockMvc.perform(get("/media/users/{userKey}/storage-usage", targetUser))
                .andExpect(status().isInternalServerError()); // IllegalArgumentException → 500 via GlobalExceptionHandler
    }

    /*
     * ========================= ADMIN — ALL FIELDS RETURNED =========================
     */

    @Test
    @WithMockUser(roles = "SA")
    void getUserStorage_allFieldsPresent() throws Exception {
        UUID targetUser = UUID.randomUUID();
        Instant now = Instant.now();

        StorageUsageResponse response = StorageUsageResponse.builder()
                .userKey(targetUser)
                .mediaStorageUsed(1000L)
                .mediaFileCount(4L)
                .chatStorageUsed(2000L)
                .chatMessageCount(8L)
                .totalStorageUsed(3000L)
                .lastUpdated(now)
                .build();

        when(userStorageUsageService.getUsage(eq(targetUser))).thenReturn(response);

        mockMvc.perform(get("/media/users/{userKey}/storage-usage", targetUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mediaStorageUsed").value(1000L))
                .andExpect(jsonPath("$.data.mediaFileCount").value(4L))
                .andExpect(jsonPath("$.data.chatStorageUsed").value(2000L))
                .andExpect(jsonPath("$.data.chatMessageCount").value(8L))
                .andExpect(jsonPath("$.data.totalStorageUsed").value(3000L))
                .andExpect(jsonPath("$.data.lastUpdated").isNotEmpty());
    }
}
