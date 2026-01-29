package com.algomeet.signalservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.security.filter.JwtAuthenticationFilter;
import com.algomeet.signalservice.service.SignedPreKeyService;
import com.algomeet.signalservice.util.MessageUtil;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
    controllers = SignedPreKeyController.class,
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class
        )
    }
)
@ContextConfiguration(classes = SignedPreKeyController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {
    MongoAutoConfiguration.class,
    MongoDataAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class SignedPreKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignedPreKeyService service;

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
     * GET SIGNED PRE-KEY
     * ------------------------------------------------- */

    @Test
    void getSignedPreKey_success() throws Exception {
        SignedPreKeyResponse response = new SignedPreKeyResponse();

        when(service.getById(eq(USER_KEY), eq(1)))
                .thenReturn(response);

        mockMvc.perform(get("/signal/v2/devices/1/signed-prekeys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void getSignedPreKey_withUserKeyParam() throws Exception {
        UUID otherUser = UUID.randomUUID();
        SignedPreKeyResponse response = new SignedPreKeyResponse();

        when(service.getById(eq(otherUser), eq(1)))
                .thenReturn(response);

        mockMvc.perform(get("/signal/v2/devices/1/signed-prekeys")
                .param("userKey", otherUser.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void getSignedPreKey_notFound() throws Exception {
        when(service.getById(eq(USER_KEY), eq(1)))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/devices/1/signed-prekeys"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SIGNED_PRE_KEY_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * UPDATE SIGNED PRE-KEY
     * ------------------------------------------------- */

    @Test
    void updateSignedPreKey_success() throws Exception {
        SignedPreKeyRequest request = new SignedPreKeyRequest();
        request.setSignedPreKeyId(String.valueOf(1));
        request.setPublicKey("BBSignedPreKeyPublicKeyBase64==");
        request.setSignature("MEUCIExampleSignatureBase64==");

        SignedPreKeyResponse response = new SignedPreKeyResponse();

        when(service.update(eq(USER_KEY), eq(1), any()))
                .thenReturn(response);

        mockMvc.perform(put("/signal/v2/devices/1/signed-prekeys")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void updateSignedPreKey_notFound() throws Exception {
        SignedPreKeyRequest request = new SignedPreKeyRequest();
        request.setSignedPreKeyId(String.valueOf(1));
        request.setPublicKey("BBSignedPreKeyPublicKeyBase64==");
        request.setSignature("MEUCIExampleSignatureBase64==");

        when(service.update(eq(USER_KEY), eq(1), any()))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(put("/signal/v2/devices/1/signed-prekeys")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SIGNED_PRE_KEY_NOT_FOUND.name()));
    }
}
