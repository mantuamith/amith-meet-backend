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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.signalservice.config.LocalizationConfig;
import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.GroupSessionBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSessionBackupService;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = GroupSessionBackupController.class)
@ContextConfiguration(classes = GroupSessionBackupController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
class GroupSessionBackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupSessionBackupService service;

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
        new com.algomeet.signalservice.util.MessageUtil(messageSource);
    }

    @AfterEach
    void tearDown() {
        if (securityUtilMock != null) {
            securityUtilMock.close();
        }
    }
    
    public GroupSessionBackupRequest getGroupSessionBackupRequest() {
        GroupSessionBackupRequest request = new GroupSessionBackupRequest();
        
        request.setGroupId("group-12345");
        request.setDistributionId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        request.setDeviceId(1);
        request.setInbound(true);
        request.setSenderUserKey(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        request.setSenderDeviceId(2);
        request.setSerializedSession("QUJDREVGR0g="); // "ABCDEFGH" Base64
        request.setAesAlg("AES/GCM/NoPadding");
        request.setVersion("v1");
        request.setSalt("QUJDREVGR0hJSktMTQ=="); // "ABCDEFGHIJKLM" Base64

        return request;
    }

    /* -------------------------------------------------
     * SAVE BACKUP
     * ------------------------------------------------- */

    @Test
    void saveBackup_success() throws Exception {
        GroupSessionBackupRequest request = getGroupSessionBackupRequest();

        GroupSessionBackupResponse response = new GroupSessionBackupResponse();

        when(service.saveBackup(eq(USER_KEY), any()))
                .thenReturn(response);

        mockMvc.perform(post("/signal/backup/group-sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void saveBackup_alreadyExists() throws Exception {
        GroupSessionBackupRequest request = getGroupSessionBackupRequest();

        when(service.saveBackup(eq(USER_KEY), any()))
                .thenThrow(new GroupSessionBackupExistsException("exists"));

        mockMvc.perform(post("/signal/backup/group-sessions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SESSION_BACKUP_EXISTS.name()));
    }

    /* -------------------------------------------------
     * GET ALL BACKUPS
     * ------------------------------------------------- */

    @Test
    void getBackups_success() throws Exception {
        GroupSessionBackupResponse backup = new GroupSessionBackupResponse();

        when(service.findBackups(USER_KEY))
                .thenReturn(List.of(backup));

        mockMvc.perform(get("/signal/backup/group-sessions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /* -------------------------------------------------
     * GET BACKUP BY DISTRIBUTION
     * ------------------------------------------------- */

    @Test
    void getBackupByDistribution_success() throws Exception {
        GroupSessionBackupResponse backup = new GroupSessionBackupResponse();
        UUID distributionId = UUID.randomUUID();

        when(service.findBackup(USER_KEY, "group-1", distributionId, true))
                .thenReturn(backup);

        mockMvc.perform(get("/signal/backup/group-sessions/{groupId}/{distributionId}/{isInbound}", 
                        "group-1", distributionId, true)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void getBackupByDistribution_notFound() throws Exception {
        UUID distributionId = UUID.randomUUID();

        when(service.findBackup(USER_KEY, "group-1", distributionId, true))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/backup/group-sessions/{groupId}/{distributionId}/{isInbound}", 
                        "group-1", distributionId, true)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * GET BACKUP BY DEVICE
     * ------------------------------------------------- */

    @Test
    void getBackupByDevice_success() throws Exception {
        GroupSessionBackupResponse backup = new GroupSessionBackupResponse();

        when(service.findBackupByDevice(USER_KEY, 1))
                .thenReturn(List.of(backup));

        mockMvc.perform(get("/signal/backup/group-sessions/{deviceId}/device", 1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /* -------------------------------------------------
     * DELETE BACKUP
     * ------------------------------------------------- */

    @Test
    void deleteBackup_success() throws Exception {
        doNothing().when(service).deleteBackup(USER_KEY, "group-1", UUID.randomUUID(), true);

        // Use a fixed UUID for request, matching what service expects is mocked
        UUID distributionId = UUID.randomUUID();
        doNothing().when(service).deleteBackup(USER_KEY, "group-1", distributionId, true);

        mockMvc.perform(delete("/signal/backup/group-sessions/{groupId}/{distributionId}/{isInbound}",
                        "group-1", distributionId, true)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteBackup_notFound() throws Exception {
        UUID distributionId = UUID.randomUUID();
        doThrow(new RecordNotFoundException("not found"))
                .when(service).deleteBackup(USER_KEY, "group-1", distributionId, true);

        mockMvc.perform(delete("/signal/backup/group-sessions/{groupId}/{distributionId}/{isInbound}",
                        "group-1", distributionId, true)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE BACKUP BY DEVICE
     * ------------------------------------------------- */

    @Test
    void deleteBackupByDevice_success() throws Exception {
        doNothing().when(service).deleteBackupByDevice(USER_KEY, 1);

        mockMvc.perform(delete("/signal/backup/group-sessions/{deviceId}/device", 1)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteBackupByDevice_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service).deleteBackupByDevice(USER_KEY, 1);

        mockMvc.perform(delete("/signal/backup/group-sessions/{deviceId}/device", 1)
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE ALL BACKUPS
     * ------------------------------------------------- */

    @Test
    void deleteAllBackups_success() throws Exception {
        doNothing().when(service).deleteAllUserBackups(USER_KEY);

        mockMvc.perform(delete("/signal/backup/group-sessions")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteAllBackups_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service).deleteAllUserBackups(USER_KEY);

        mockMvc.perform(delete("/signal/backup/group-sessions")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.GROUP_SESSION_BACKUP_NOT_FOUND.name()));
    }
}
