package com.algomeet.mediaservice.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

import com.algomeet.mediaservice.config.LocalizationConfig;
import com.algomeet.mediaservice.dto.StorageUsageResponse;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;
import com.algomeet.mediaservice.util.MessageUtil;
import com.algomeet.mediaservice.util.SecurityUtil;


@WebMvcTest(controllers = UserStorageUsageController.class)
@ContextConfiguration(classes = UserStorageUsageController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration
@AutoConfigureMockMvc(addFilters = false)
class UserStorageUsageControllerTest {

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
}
