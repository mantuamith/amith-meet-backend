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

import java.time.Instant;
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
import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.security.filter.JwtAuthenticationFilter;
import com.algomeet.signalservice.service.OneTimePreKeyService;
import com.algomeet.signalservice.util.MessageUtil;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
        controllers = OneTimePreKeyController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = JwtAuthenticationFilter.class
                )
        }
)
@ContextConfiguration(classes = OneTimePreKeyController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class OneTimePreKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OneTimePreKeyService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageSource messageSource;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
     * CREATE ONE-TIME PREKEYS
     * ------------------------------------------------- */
    @Test
    void createPrekeys_success() throws Exception {
        OneTimePreKeysRequest request = new OneTimePreKeysRequest();
        
        OneTimePreKeyRequest otpkRequest = new OneTimePreKeyRequest();
        otpkRequest.setPreKeyId(String.valueOf(1)); // must be >= 1
        otpkRequest.setPublicKey("BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7="); // sample Base64 public key        
        request.setPreKeys(List.of(otpkRequest));
        
        OneTimePreKeyResponse otpkResp = new OneTimePreKeyResponse(
                1L, // id
                UUID.fromString("11111111-1111-1111-1111-111111111111"), // userKey
                1, // deviceId
                "1", // preKeyId
                "BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7=", // publicKey
                false, // used
                Instant.now() // createdAt
            );
        
        when(service.create(eq(USER_KEY), eq(1), any()))
                .thenReturn(List.of(otpkResp));

        mockMvc.perform(post("/signal/v2/devices/1/prekeys/one-time")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void createPrekeys_deviceNotFound() throws Exception {
        OneTimePreKeysRequest request = new OneTimePreKeysRequest();
        
        OneTimePreKeyRequest otpkRequest = new OneTimePreKeyRequest();
        otpkRequest.setPreKeyId(String.valueOf(1)); // must be >= 1
        otpkRequest.setPublicKey("BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7="); // sample Base64 public key
        
        request.setPreKeys(List.of(otpkRequest));

        when(service.create(eq(USER_KEY), eq(1), any()))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(post("/signal/v2/devices/1/prekeys/one-time")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * GET ONE-TIME PREKEYS
     * ------------------------------------------------- */
    @Test
    void getPrekeys_success() throws Exception {    	
        OneTimePreKeyResponse otpkResp = new OneTimePreKeyResponse(
                1L, // id
                UUID.fromString("11111111-1111-1111-1111-111111111111"), // userKey
                1, // deviceId
                "1", // preKeyId
                "BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7=", // publicKey
                false, // used
                Instant.now() // createdAt
            );
        
        when(service.getPrekeys(eq(USER_KEY), eq(1)))
                .thenReturn(List.of(otpkResp));

        mockMvc.perform(get("/signal/v2/devices/1/prekeys/one-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getPrekeys_deviceNotFound() throws Exception {
        when(service.getPrekeys(eq(USER_KEY), eq(1)))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/devices/1/prekeys/one-time"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * GET AVAILABLE ONE-TIME PREKEY COUNT
     * ------------------------------------------------- */
    @Test
    void getAvailablePrekeysCount_success() throws Exception {
        when(service.getAvailablePrekeysCount(eq(USER_KEY), eq(1)))
                .thenReturn(5L);

        mockMvc.perform(get("/signal/v2/devices/1/prekeys/one-time/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data").value(5));
    }

    @Test
    void getAvailablePrekeysCount_deviceNotFound() throws Exception {
        when(service.getAvailablePrekeysCount(eq(USER_KEY), eq(1)))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/devices/1/prekeys/one-time/count"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE ALL ONE-TIME PREKEYS
     * ------------------------------------------------- */
    @Test
    void deleteAll_success() throws Exception {
        doNothing().when(service).delete(USER_KEY, 1);

        mockMvc.perform(delete("/signal/v2/devices/1/prekeys/one-time")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteAll_notFound() throws Exception {
        doThrow(new RecordNotFoundException("not found"))
                .when(service).delete(USER_KEY, 1);

        mockMvc.perform(delete("/signal/v2/devices/1/prekeys/one-time")
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.ONE_TIME_PRE_KEY_NOT_FOUND.name()));
    }
}
