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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
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
import com.algomeet.signalservice.dto.GroupSenderKeyBackupRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupUpdateRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.GroupSenderKeyBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSenderKeyBackupService;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
        controllers = GroupSenderKeyBackupController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE)
)
@ContextConfiguration(classes = GroupSenderKeyBackupController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class GroupSenderKeyBackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupSenderKeyBackupService service;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockedStatic<SecurityUtil> securityUtilMock;

    @Autowired
    MessageSource messageSource;

    @BeforeEach
    void setup() {
        securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getUserKey).thenReturn(USER_KEY.toString());
    }

    @AfterEach
    void tearDown() {
        if (securityUtilMock != null) {
            securityUtilMock.close();
        }
    }

    /* -------------------------------------------------
     * CREATE BACKUP
     * ------------------------------------------------- */
    @Test
    void createBackup_success() throws Exception {
        GroupSenderKeyBackupRequest request = new GroupSenderKeyBackupRequest();

        // Required fields
        request.setGroupId("group-123");
        request.setDistributionId(UUID.randomUUID());
        request.setSerializedSkdm("QUJDREVGR0g="); // Base64 example, valid pattern

        // Optional fields
        request.setAesAlg("AES/GCM/NoPadding"); // max 32 chars
        request.setVersion("v1"); // max 10 chars
        request.setSalt("U0FsdGVkX1+Zm9yVGVzdA=="); // Base64, max 88 chars

        GroupSenderKeyBackupResponse response = new GroupSenderKeyBackupResponse();

        when(service.save(eq(USER_KEY), any())).thenReturn(response);

        mockMvc.perform(post("/signal/group-sender-key-backups")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void createBackup_alreadyExists() throws Exception {
    	GroupSenderKeyBackupRequest request = new GroupSenderKeyBackupRequest();

        // Required fields
        request.setGroupId("group-123");
        request.setDistributionId(UUID.randomUUID());
        request.setSerializedSkdm("QUJDREVGR0g="); // Base64 example, valid pattern

        // Optional fields
        request.setAesAlg("AES/GCM/NoPadding"); // max 32 chars
        request.setVersion("v1"); // max 10 chars
        request.setSalt("U0FsdGVkX1+Zm9yVGVzdA=="); // Base64, max 88 chars

        when(service.save(eq(USER_KEY), any()))
                .thenThrow(new GroupSenderKeyBackupExistsException("exists"));

        mockMvc.perform(post("/signal/group-sender-key-backups")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SENDER_KEY_BACKUP_EXISTS.name()));
    }

    /* -------------------------------------------------
     * UPDATE BACKUP
     * ------------------------------------------------- */
    @Test
    void updateBackup_success() throws Exception {
    	GroupSenderKeyBackupUpdateRequest request = new GroupSenderKeyBackupUpdateRequest();

        // Required field
        request.setSerializedSkdm("U29tZVNhbXBsZVNlbmRlclNLRE1EYXRh"); // valid Base64, <=300 chars

        // Optional fields
        request.setAesAlg("AES/GCM/NoPadding"); // <=32 chars
        request.setVersion("v1");               // <=10 chars
        request.setSalt("U0FsdGVkX1NhbXBsZVNhbHQ="); // valid Base64, <=88 chars

        GroupSenderKeyBackupResponse response = new GroupSenderKeyBackupResponse();

        when(service.update(eq(USER_KEY), eq("group-123"), any(UUID.class), any()))
                .thenReturn(response);

        mockMvc.perform(put("/signal/group-sender-key-backups/group-123/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void updateBackup_notFound() throws Exception {
    	GroupSenderKeyBackupUpdateRequest request = new GroupSenderKeyBackupUpdateRequest();

        // Required field
        request.setSerializedSkdm("U29tZVNhbXBsZVNlbmRlclNLRE1EYXRh"); // valid Base64, <=300 chars

        // Optional fields
        request.setAesAlg("AES/GCM/NoPadding"); // <=32 chars
        request.setVersion("v1");               // <=10 chars
        request.setSalt("U0FsdGVkX1NhbXBsZVNhbHQ="); // valid Base64, <=88 chars

        when(service.update(eq(USER_KEY), eq("group-123"), any(UUID.class), any()))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(put("/signal/group-sender-key-backups/group-123/" + UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SENDER_KEY_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * GET BACKUP
     * ------------------------------------------------- */
    @Test
    void getBackup_success() throws Exception {
        GroupSenderKeyBackupResponse response = new GroupSenderKeyBackupResponse();
        UUID distributionId = UUID.randomUUID();

        when(service.findById(USER_KEY, "group-123", distributionId))
                .thenReturn(Optional.of(response));

        mockMvc.perform(get("/signal/group-sender-key-backups/group-123/" + distributionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void getBackup_notFound() throws Exception {
        UUID distributionId = UUID.randomUUID();

        when(service.findById(USER_KEY, "group-123", distributionId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/signal/group-sender-key-backups/group-123/" + distributionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SENDER_KEY_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * GET BY USER
     * ------------------------------------------------- */
    @Test
    void getByUser_success() throws Exception {
        when(service.findByUser(USER_KEY))
                .thenReturn(List.of(new GroupSenderKeyBackupResponse()));

        mockMvc.perform(get("/signal/group-sender-key-backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /* -------------------------------------------------
     * GET BY GROUP
     * ------------------------------------------------- */
    @Test
    void getByGroup_success() throws Exception {
        when(service.findByGroup("group-123"))
                .thenReturn(List.of(new GroupSenderKeyBackupResponse()));

        mockMvc.perform(get("/signal/group-sender-key-backups/group-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /* -------------------------------------------------
     * DELETE BACKUP
     * ------------------------------------------------- */
    @Test
    void deleteBackup_success() throws Exception {
        doNothing().when(service).delete(eq(USER_KEY), eq("group-123"), any(UUID.class));

        mockMvc.perform(delete("/signal/group-sender-key-backups/group-123/" + UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteBackup_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service).delete(eq(USER_KEY), eq("group-123"), any(UUID.class));

        mockMvc.perform(delete("/signal/group-sender-key-backups/group-123/" + UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SENDER_KEY_BACKUP_NOT_FOUND.name()));
    }
}
