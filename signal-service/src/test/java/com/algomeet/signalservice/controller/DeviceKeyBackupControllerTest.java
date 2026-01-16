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
import com.algomeet.signalservice.dto.DeviceKeyBackupRequest;
import com.algomeet.signalservice.dto.DeviceKeyBackupResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupUpdateRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.DeviceKeyBackupService;
import com.algomeet.signalservice.util.MessageUtil;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
        controllers = DeviceKeyBackupController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {}
                )
        }
)
@ContextConfiguration(classes = DeviceKeyBackupController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class DeviceKeyBackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceKeyBackupService service;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private MockedStatic<SecurityUtil> securityUtilMock;

    @Autowired
    private MessageSource messageSource;

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
    	DeviceKeyBackupRequest request = new DeviceKeyBackupRequest();
    	request.setDeviceId(1);
    	request.setRegistrationId(1001);

    	// Base64-encoded values (valid and within size limits)
    	request.setSerializedIdentityKey("SW50ZW50aXR5S2V5RGF0YQ==");

    	request.setSerializedPreKeys(List.of(
    	        "UHJlS2V5RGF0YTE=",
    	        "UHJlS2V5RGF0YTI="
    	));

    	request.setSerializedSignedPreKey("U2lnbmVkUHJlS2V5RGF0YQ==");

    	request.setSerializedKyberPreKey(
    	        "S3liZXJQcmVLZXlEYXRhVmFsaWQ="
    	);

    	// Optional metadata (valid sizes)
    	request.setAesAlg("AES/GCM/NoPadding");
    	request.setVersion("v1");
    	request.setSalt("c2FsdFZhbHVlMTIz");

        DeviceKeyBackupResponse response = new DeviceKeyBackupResponse();

        when(service.saveBackup(eq(USER_KEY), any()))
                .thenReturn(response);

        mockMvc.perform(post("/signal/backup/device-keys")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * UPDATE BACKUP
     * ------------------------------------------------- */
    @Test
    void updateBackup_success() throws Exception {
    	DeviceKeyBackupRequest request = new DeviceKeyBackupRequest();
    	request.setDeviceId(1);
    	request.setRegistrationId(1001);

    	// Base64-encoded values (valid and within size limits)
    	request.setSerializedIdentityKey("SW50ZW50aXR5S2V5RGF0YQ==");

    	request.setSerializedPreKeys(List.of(
    	        "UHJlS2V5RGF0YTE=",
    	        "UHJlS2V5RGF0YTI="
    	));

    	request.setSerializedSignedPreKey("U2lnbmVkUHJlS2V5RGF0YQ==");

    	request.setSerializedKyberPreKey(
    	        "S3liZXJQcmVLZXlEYXRhVmFsaWQ="
    	);

    	// Optional metadata (valid sizes)
    	request.setAesAlg("AES/GCM/NoPadding");
    	request.setVersion("v1");
    	request.setSalt("c2FsdFZhbHVlMTIz");

        DeviceKeyBackupResponse response = new DeviceKeyBackupResponse();

        when(service.updateBackup(eq(USER_KEY), eq(1), any()))
                .thenReturn(response);

        mockMvc.perform(put("/signal/backup/device-keys/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * GET BACKUP
     * ------------------------------------------------- */
    @Test
    void getBackup_success() throws Exception {
        DeviceKeyBackupResponse response = new DeviceKeyBackupResponse();
        when(service.restoreBackup(USER_KEY, 1)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/signal/backup/device-keys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void getBackup_notFound() throws Exception {
        when(service.restoreBackup(USER_KEY, 1)).thenReturn(Optional.empty());

        mockMvc.perform(get("/signal/backup/device-keys/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.DEVICE_KEY_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE BACKUP
     * ------------------------------------------------- */
    @Test
    void deleteBackup_success() throws Exception {
        doNothing().when(service).deleteBackup(USER_KEY, 1);

        mockMvc.perform(delete("/signal/backup/device-keys/1")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteBackup_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service).deleteBackup(USER_KEY, 1);

        mockMvc.perform(delete("/signal/backup/device-keys/1")
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.DEVICE_KEY_BACKUP_NOT_FOUND.name()));
    }
}
