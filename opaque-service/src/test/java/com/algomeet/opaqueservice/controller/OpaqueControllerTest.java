package com.algomeet.opaqueservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Base64;
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
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.opaqueservice.config.LocalizationConfig;
import com.algomeet.opaqueservice.dto.*;
import com.algomeet.opaqueservice.entity.UserSecureStore;
import com.algomeet.opaqueservice.entity.UserSecureStoreId;
import com.algomeet.opaqueservice.enums.CredentialType;
import com.algomeet.opaqueservice.enums.ResponseCode;
import com.algomeet.opaqueservice.jni.Opaque;
import com.algomeet.opaqueservice.jni.dto.OpaqueCredResp;
import com.algomeet.opaqueservice.jni.dto.OpaqueRegResp;
import com.algomeet.opaqueservice.service.UserSecureStoreService;
import com.algomeet.opaqueservice.util.MessageUtil;
import com.algomeet.opaqueservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(
        controllers = OpaqueController.class,
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {}
                )
        }
)
@ContextConfiguration(classes = OpaqueController.class)
@Import(LocalizationConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class OpaqueControllerTest {

    private static final UUID USER_KEY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserSecureStoreService userSecureStoreService;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    @MockBean
    private Opaque opaque;

    @MockBean
    private ValueOperations<String, String> valueOps;

    private MockedStatic<SecurityUtil> securityUtilMock;
    
    @Autowired
    private MessageSource messageSource;

    @BeforeEach
    void setup() {
        securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getUserKey)
                .thenReturn(USER_KEY.toString());

        when(redisTemplate.opsForValue()).thenReturn(valueOps);        
        new MessageUtil(messageSource);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    /* -------------------------------------------------
     * REGISTER
     * ------------------------------------------------- */
    @Test
    void register_success() throws Exception {
        RegistrationRequest req = new RegistrationRequest();
        req.setType(CredentialType.PIN);
        req.setClientRegistrationMessage(
                Base64.getEncoder().encodeToString("client".getBytes()));

        OpaqueRegResp regResp = new OpaqueRegResp();
        regResp.pub = "pub".getBytes();
        regResp.sec = "sec".getBytes();

        when(opaque.createRegResp(any(), any()))
                .thenReturn(regResp);

        mockMvc.perform(post("/opaque/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * STORE MASTER SECRET
     * ------------------------------------------------- */
    @Test
    void saveMasterSecret_success() throws Exception {
        UserMasterSecretRequest req = new UserMasterSecretRequest();
        req.setType(CredentialType.PIN);
        req.setRecord(Base64.getEncoder()
                .encodeToString("record".getBytes()));

        when(userSecureStoreService.getMasterSecret(USER_KEY, CredentialType.PIN))
                .thenReturn(null);

        when(valueOps.get(any()))
                .thenReturn(Base64.getEncoder()
                        .encodeToString("sec".getBytes()));

        when(opaque.storeRec(any(), any()))
                .thenReturn("finalRec".getBytes());

        UserSecureStore store = Mockito.mock(UserSecureStore.class);
        UserSecureStoreId userSecureStoreId = new UserSecureStoreId();
        userSecureStoreId.setUserKey(USER_KEY);
        userSecureStoreId.setType(CredentialType.PIN);
        
        when(store.getId())
                .thenReturn(userSecureStoreId);

        when(userSecureStoreService.save(eq(USER_KEY), any(), any()))
                .thenReturn(store);

        mockMvc.perform(post("/opaque/master-secret/store")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void saveMasterSecret_conflict() throws Exception {
        UserMasterSecretRequest req = new UserMasterSecretRequest();
        req.setType(CredentialType.PIN);

        when(userSecureStoreService.getMasterSecret(USER_KEY, CredentialType.PIN))
                .thenReturn(Mockito.mock(UserSecureStore.class));

        mockMvc.perform(post("/opaque/master-secret/store")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.MASTER_SECRET_KEY_ALRREADY_EXISTS.name()));
    }

    /* -------------------------------------------------
     * UPDATE MASTER SECRET
     * ------------------------------------------------- */
    @Test
    void updateMasterSecret_success() throws Exception {
        UserMasterSecretRequest req = new UserMasterSecretRequest();
        req.setType(CredentialType.PIN);
        req.setRecord(Base64.getEncoder()
                .encodeToString("record".getBytes()));

        when(valueOps.get(any()))
                .thenReturn(Base64.getEncoder()
                        .encodeToString("sec".getBytes()));

        when(opaque.storeRec(any(), any()))
                .thenReturn("finalRec".getBytes());

        UserSecureStore store = Mockito.mock(UserSecureStore.class);
        
        UserSecureStoreId userSecureStoreId = new UserSecureStoreId();
        userSecureStoreId.setUserKey(USER_KEY);
        userSecureStoreId.setType(CredentialType.PIN);
        when(store.getId())
                .thenReturn(userSecureStoreId);

        when(userSecureStoreService.save(eq(USER_KEY), any(), any()))
                .thenReturn(store);

        mockMvc.perform(put("/opaque/master-secret/store")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * CREDENTIAL RESPONSE
     * ------------------------------------------------- */
    @Test
    void credentialResponse_success() throws Exception {
        UserCredentialRequest req = new UserCredentialRequest();
        req.setType(CredentialType.PIN);
        req.setClientPublicKey(
                Base64.getEncoder().encodeToString("clientPub".getBytes()));

        UserSecureStore store = Mockito.mock(UserSecureStore.class);
        when(store.getRec())
                .thenReturn(Base64.getEncoder()
                        .encodeToString("rec".getBytes()));

        when(userSecureStoreService.getMasterSecret(USER_KEY, CredentialType.PIN))
                .thenReturn(store);

        OpaqueCredResp credResp = new OpaqueCredResp();        
        credResp.pub = "pub".getBytes();
        credResp.sec = "sec".getBytes();

        when(opaque.createCredResp(any(), any(), any(), any()))
                .thenReturn(credResp);

        mockMvc.perform(post("/opaque/credential-response")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void credentialResponse_notFound() throws Exception {
        UserCredentialRequest req = new UserCredentialRequest();
        req.setType(CredentialType.PIN);

        when(userSecureStoreService.getMasterSecret(USER_KEY, CredentialType.PIN))
                .thenReturn(null);

        mockMvc.perform(post("/opaque/credential-response")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.MASTER_SECRET_KEY_NOT_FOUND.name()));
    }
}
