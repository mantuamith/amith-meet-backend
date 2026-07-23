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
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.dto.GroupSenderKeysRequest;
import com.algomeet.signalservice.dto.GroupSenderKeysResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.GroupSenderKeyService;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = GroupSenderKeyController.class)
@ContextConfiguration(classes = GroupSenderKeyController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
class GroupSenderKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GroupSenderKeyService service;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-2222-111111111111");

    private MockedStatic<SecurityUtil> securityUtilMock;

    @Autowired
    private MessageSource messageSource;

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
     * CREATE GROUP SENDER KEY
     * ------------------------------------------------- */
    @Test
    void createGroupSenderKey_success() throws Exception {
    	GroupSenderKeysRequest request = new GroupSenderKeysRequest();  
        GroupSenderKeyRequest key = new GroupSenderKeyRequest();       
        key.setReceiverUserKey(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        key.setReceiverDeviceId(1);
        key.setSkdmCipher("U29tZVNhbXBsZVNlbmRlclNLRE1EYXRh"); // valid Base64 string
        
        request.setKeys(List.of(key));

        GroupSenderKeysResponse response = new GroupSenderKeysResponse();

        when(service.create(USER_KEY, 1, GROUP_ID, request))
                .thenReturn(response);

        mockMvc.perform(post("/signal/v2/devices/1/groups/group-1/sender-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void createGroupSenderKey_deviceNotFound() throws Exception {
        GroupSenderKeysRequest request = new GroupSenderKeysRequest();
        GroupSenderKeyRequest key = new GroupSenderKeyRequest();
        key.setReceiverUserKey(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        key.setReceiverDeviceId(1);
        key.setSkdmCipher("U29tZVNhbXBsZVNlbmRlclNLRE1EYXRh"); // valid Base64 string
        request.setKeys(List.of(key));
        
        when(service.create(USER_KEY, 1, GROUP_ID, request))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(post("/signal/v2/devices/1/groups/group-1/sender-keys")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * GET GROUP SENDER KEYS
     * ------------------------------------------------- */
    @Test
    void getGroupSenderKeys_success() throws Exception {
        GroupSenderKeyResponse response = new GroupSenderKeyResponse();

        when(service.getList(USER_KEY, 1, GROUP_ID))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/signal/v2/devices/1/groups/group-1/sender-keys")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getGroupSenderKeys_notFound() throws Exception {
        when(service.getList(USER_KEY, 1, GROUP_ID))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/devices/1/groups/group-1/sender-keys")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_ID_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * POLL GROUP SENDER KEYS
     * ------------------------------------------------- */
    @Test
    void pollGroupSenderKeys_success() throws Exception {
        GroupSenderKeyResponse response = new GroupSenderKeyResponse();

        when(service.longPoll(USER_KEY, 1, GROUP_ID, 100))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/signal/v2/devices/1/groups/group-1/sender-keys/poll")
                        .param("timeoutMs", "100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void pollGroupSenderKeys_notFound() throws Exception {
        when(service.longPoll(USER_KEY, 1, GROUP_ID, 100))
                .thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/v2/devices/1/groups/group-1/sender-keys/poll")
                        .param("timeoutMs", "100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND.name()));
    }   
}
