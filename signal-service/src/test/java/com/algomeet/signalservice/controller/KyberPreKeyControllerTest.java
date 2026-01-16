package com.algomeet.signalservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;
import com.algomeet.signalservice.entity.KyberPreKeyId;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.KyberPreKeyService;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
        controllers = KyberPreKeyController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {}
                )
        }
)
@ContextConfiguration(classes = KyberPreKeyController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class KyberPreKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KyberPreKeyService service;

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
    
    public KyberPreKeyResponse getKyberPreKeyResponse() {
        KyberPreKeyResponse response = new KyberPreKeyResponse(
            UUID.randomUUID().toString(),          // userKey
            1,                                      // deviceId
            123,                                    // kyberPreKeyId
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnSampleKyberPublicKeyBase64==", // publicKey
            "MEUCIFakeSignatureBase64Example==",   // signature
            Instant.now(),                          // createdAt
            Instant.now()                           // updatedAt
        );
        
        return response;
    }

    /* -------------------------------------------------
     * GET KYBER PRE-KEY
     * ------------------------------------------------- */
    @Test
    void retrieve_success() throws Exception {
        KyberPreKeyResponse response = getKyberPreKeyResponse();
        when(service.getPreKey(any(KyberPreKeyId.class))).thenReturn(response);

        mockMvc.perform(get("/signal/v2/devices/1/kyber-prekeys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void retrieve_notFound() throws Exception {
        when(service.getPreKey(any(KyberPreKeyId.class)))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/devices/1/kyber-prekeys"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.KYBER_PRE_KEY_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * UPDATE KYBER PRE-KEY
     * ------------------------------------------------- */
    @Test
    void update_success() throws Exception {
        KyberPreKeyRequest request = new KyberPreKeyRequest();
        request.setKyberPreKeyId(1);
        request.setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnSampleKyberPublicKeyBase64==");
        request.setSignature("MEUCIFakeSignatureBase64Example==");

        KyberPreKeyResponse response = getKyberPreKeyResponse();
        when(service.updatePreKey(any(KyberPreKeyId.class), eq(request)))
                .thenReturn(response);

        mockMvc.perform(put("/signal/v2/devices/1/kyber-prekeys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void update_notFound() throws Exception {
        KyberPreKeyRequest request = new KyberPreKeyRequest();
        request.setKyberPreKeyId(1);
        request.setPublicKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnSampleKyberPublicKeyBase64==");
        request.setSignature("MEUCIFakeSignatureBase64Example==");

        when(service.updatePreKey(any(KyberPreKeyId.class), eq(request)))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(put("/signal/v2/devices/1/kyber-prekeys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.KYBER_PRE_KEY_NOT_FOUND.name()));
    }
}
