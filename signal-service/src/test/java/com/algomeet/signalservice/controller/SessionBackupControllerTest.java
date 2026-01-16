package com.algomeet.signalservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.signalservice.config.LocalizationConfig;
import com.algomeet.signalservice.dto.SessionBackupRequest;
import com.algomeet.signalservice.dto.SessionBackupResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.security.filter.JwtAuthenticationFilter;
import com.algomeet.signalservice.service.SessionBackupService;
import com.algomeet.signalservice.util.MessageUtil;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
    controllers = SessionBackupController.class,
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class
        )
    }
)
@ContextConfiguration(classes = SessionBackupController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class SessionBackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionBackupService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    MessageSource messageSource;

    private static final UUID USER_KEY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setup() {
        securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getUserKey)
                .thenReturn(USER_KEY.toString());

        new MessageUtil(messageSource);
    }

    @AfterEach
    void tearDown() {
        if (securityUtilMock != null) {
            securityUtilMock.close();
        }
    }

    /* -------------------------------------------------
     * SAVE BACKUP
     * ------------------------------------------------- */

    @Test
    void saveBackup_success() throws Exception {
        SessionBackupRequest request = new SessionBackupRequest();
        request.setRegistrationId(1);
        request.setRemoteUserKey(UUID.randomUUID());
        request.setRemoteDeviceId(1);
        //request.setCiphertext("ENCRYPTED_SESSION_DATA_BASE64");

        SessionBackupResponse response = SessionBackupResponse.builder().build();

        when(service.saveBackup(eq(USER_KEY), eq(1), any()))
                .thenReturn(response);

        mockMvc.perform(post("/signal/backup/devices/1/sessions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void saveBackup_deviceNotFound() throws Exception {
        when(service.saveBackup(eq(USER_KEY), eq(1), any()))
                .thenThrow(new RecordNotFoundException("not found"));

        SessionBackupRequest request = new SessionBackupRequest();
        request.setRegistrationId(1);
        request.setRemoteUserKey(UUID.randomUUID());
        request.setRemoteDeviceId(1);
        //request.setCiphertext("ENCRYPTED_SESSION_DATA_BASE64");

        mockMvc.perform(post("/signal/backup/devices/1/sessions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * RESTORE SESSIONS
     * ------------------------------------------------- */

    @Test
    void restoreSessions_success() throws Exception {
        when(service.restoreSessions(eq(USER_KEY), eq(1)))
                .thenReturn(List.of(SessionBackupResponse.builder().build()));

        mockMvc.perform(get("/signal/backup/devices/1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /* -------------------------------------------------
     * DELETE SESSIONS BY DEVICE
     * ------------------------------------------------- */

    @Test
    void deleteSessions_success() throws Exception {
        doNothing().when(service)
                .deleteByDeviceId(USER_KEY, 1);

        mockMvc.perform(delete("/signal/backup/devices/1/sessions")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteSessions_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service).deleteByDeviceId(USER_KEY, 1);

        mockMvc.perform(delete("/signal/backup/devices/1/sessions")
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.USER_SESSION_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE BY REGISTRATION & REMOTE USER
     * ------------------------------------------------- */

    @Test
    void deleteByDeviceRegistrationAndRemoteUser_success() throws Exception {
        doNothing().when(service)
                .deleteByDeviceRegistrationAndRemoteUser(
                        eq(USER_KEY),
                        eq(1),
                        eq(1),
                        any(UUID.class),
                        eq(2)
                );

        mockMvc.perform(delete("/signal/backup/devices/1/sessions/registration-and-remote-user")
                .param("registrationId", "1")
                .param("remoteUserKey", UUID.randomUUID().toString())
                .param("remoteDeviceId", "2")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteByDeviceRegistrationAndRemoteUser_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service)
                .deleteByDeviceRegistrationAndRemoteUser(
                        eq(USER_KEY),
                        eq(1),
                        eq(1),
                        any(UUID.class),
                        eq(2)
                );

        mockMvc.perform(delete("/signal/backup/devices/1/sessions/registration-and-remote-user")
                .param("registrationId", "1")
                .param("remoteUserKey", UUID.randomUUID().toString())
                .param("remoteDeviceId", "2")
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.USER_SESSION_BACKUP_NOT_FOUND.name()));
    }
}
