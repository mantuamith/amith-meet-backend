package com.algomeet.signalservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.signalservice.config.LocalizationConfig;
import com.algomeet.signalservice.dto.DeviceKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.UserDeviceService;
import com.algomeet.signalservice.util.SecurityUtil;

@WebMvcTest(controllers = KeyController.class)
@ContextConfiguration(classes = KeyController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
class KeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDeviceService service;

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
	
    /* -------------------------------------------------
     * GET KEYS
     * ------------------------------------------------- */

    @Test
    void getKeys_success() throws Exception {
        DeviceKeyResponse keyResponse = new DeviceKeyResponse();
        keyResponse.setDeviceId(1);
        keyResponse.setIdentityKey("BdJebLEFJpRZ4an3TEi8GgDcumAL++rMV/T3auE2885D");

        when(service.getDeviceKeys(USER_KEY, Optional.empty()))
                .thenReturn(List.of(keyResponse));

        mockMvc.perform(get("/signal/v2/keys/{userKey}", USER_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviceId").value(1))
                .andExpect(jsonPath("$.data[0].identityKey").value("BdJebLEFJpRZ4an3TEi8GgDcumAL++rMV/T3auE2885D"));
    }

    @Test
    void getKeys_withDeviceIdsParam_success() throws Exception {
        DeviceKeyResponse keyResponse = new DeviceKeyResponse();
        keyResponse.setDeviceId(2);
        keyResponse.setIdentityKey("AnotherIdentityKey");

        when(service.getDeviceKeys(USER_KEY, Optional.of(List.of(2))))
                .thenReturn(List.of(keyResponse));

        mockMvc.perform(get("/signal/v2/keys/{userKey}", USER_KEY)
                        .param("deviceIds", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviceId").value(2))
                .andExpect(jsonPath("$.data[0].identityKey").value("AnotherIdentityKey"));
    }

    @Test
    void getKeys_notFound() throws Exception {
        when(service.getDeviceKeys(USER_KEY, Optional.empty()))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/keys/{userKey}", USER_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }
}
