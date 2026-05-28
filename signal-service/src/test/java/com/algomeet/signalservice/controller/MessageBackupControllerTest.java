package com.algomeet.signalservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.MessageBackupResponse;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.MessageBackupService;
import com.algomeet.signalservice.util.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;

@WebMvcTest(
        controllers = MessageBackupController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {})
        }
)
@ContextConfiguration(classes = MessageBackupController.class)
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@AutoConfigureMockMvc(addFilters = false)
class MessageBackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageBackupService messageBackupService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_KEY = "11111111-1111-1111-1111-111111111111";

    private MockedStatic<SecurityUtil> securityUtilMock;

    @Autowired
    MessageSource messageSource;
    
	private static UUID userKey;
	private static UUID senderKey;
	private static UUID receiverKey;

    @BeforeEach
    void setup() {
		userKey = UuidCreator.getTimeOrderedEpoch();
		senderKey = UuidCreator.getTimeOrderedEpoch();
		receiverKey = UuidCreator.getTimeOrderedEpoch();
		
        securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
        securityUtilMock.when(SecurityUtil::getUserKey)
                .thenReturn(USER_KEY);

        new com.algomeet.signalservice.util.MessageUtil(messageSource);
    }

    @AfterEach
    void tearDown() {
        if (securityUtilMock != null) {
            securityUtilMock.close();
        }
    }
    
    public static MessageBackupDocument sampleMessageBackupDocument() {
        MessageBackupDocument doc = new MessageBackupDocument();

        doc.setMessageId(UuidCreator.getTimeOrderedEpoch()); // max 56 chars
        doc.setUserKey(userKey); // deprecated, still must be <= 45 chars
        doc.setSenderKey(senderKey); // <= 45 chars, not empty
        doc.setReceiverKey(receiverKey); // <= 45 chars, not empty

        // Base64-encoded encrypted message, <= 20000 chars, not empty
        doc.setEncryptedMessage("U29tZUVuY3J5cHRlZE1lc3NhZ2VCYXNlNjQ="); 

        doc.setAlgorithm("AES/GCM/NoPadding"); // <= 32 chars
        doc.setVersion("v1"); // <= 10 chars

        // Optional salt, base64 format, <= 88 chars
        doc.setSalt("c2FtcGxlU2FsdFZhbHVlQmFzZTY0"); 

        doc.setTimestamp(Instant.now());

        return doc;
    }

    /* -------------------------------------------------
     * SAVE MESSAGE
     * ------------------------------------------------- */
    @Test
    void saveMessage_success() throws Exception {
        MessageBackupDocument request = sampleMessageBackupDocument();

        when(messageBackupService.insert(any())).thenReturn(request);

        mockMvc.perform(post("/signal/backup/chat-messages")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * GET MESSAGES
     * ------------------------------------------------- */
    @Test
    void getMessages_success() throws Exception {
        MessageBackupDocument doc = sampleMessageBackupDocument();
        UUID messageId = UuidCreator.getTimeOrderedEpoch();
        when(messageBackupService.getMessages(List.of(messageId))).thenReturn(List.of(doc));

        mockMvc.perform(get("/signal/backup/chat-messages")
                .param("messageIds", messageId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getMessage_success() throws Exception {
        MessageBackupDocument doc = sampleMessageBackupDocument();

        UUID messageId = UuidCreator.getTimeOrderedEpoch();
        when(messageBackupService.getMessage(doc.getUserKey(), messageId)).thenReturn(doc);

        mockMvc.perform(get("/signal/backup/chat-messages/msg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.messageId").value("msg-1234567890abcdef"));
    }

    @Test
    void getMessage_notFound() throws Exception {
    	UUID messageId = UuidCreator.getTimeOrderedEpoch();
        when(messageBackupService.getMessage(userKey, eq(messageId))).thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(get("/signal/backup/chat-messages/msg-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.MESSAGE_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * UPDATE MESSAGE
     * ------------------------------------------------- */
    @Test
    void updateMessage_success() throws Exception {
        MessageBackupDocument request = sampleMessageBackupDocument();
        UUID messageId = UuidCreator.getTimeOrderedEpoch();
        MessageBackupDocument saved = sampleMessageBackupDocument();

        when(messageBackupService.update(userKey, eq(messageId), any())).thenReturn(saved);

        mockMvc.perform(put("/signal/backup/chat-messages/msg-1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()))
                .andExpect(jsonPath("$.data.messageId").value("msg-1234567890abcdef"));
    }

    @Test
    void updateMessage_notFound() throws Exception {
        MessageBackupDocument request = sampleMessageBackupDocument();
        UUID messageId = UuidCreator.getTimeOrderedEpoch();
        when(messageBackupService.update(userKey, eq(messageId), any())).thenThrow(new RecordNotFoundException("not found"));

        mockMvc.perform(put("/signal/backup/chat-messages/msg-1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.MESSAGE_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE MESSAGE
     * ------------------------------------------------- */
    @Test
    void deleteMessage_success() throws Exception {
    	UUID messageId = UuidCreator.getTimeOrderedEpoch();
        doNothing().when(messageBackupService).delete(userKey, eq(List.of(messageId)));

        mockMvc.perform(delete("/signal/backup/chat-messages/msg-1")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    @Test
    void deleteMessage_notFound() throws Exception {
    	UUID messageId = UuidCreator.getTimeOrderedEpoch();
        doThrow(new RecordNotFoundException("not found"))
                .when(messageBackupService).delete(userKey, eq(List.of(messageId)));

        mockMvc.perform(delete("/signal/backup/chat-messages/msg-1")
                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResponseCode.MESSAGE_BACKUP_NOT_FOUND.name()));
    }

    /* -------------------------------------------------
     * DELETE CONVERSATION
     * ------------------------------------------------- */
    @Test
    void deleteByConversation_success() throws Exception {
        doNothing().when(messageBackupService).deleteConversation(userKey, userKey);

        mockMvc.perform(delete("/signal/backup/chat-messages/peer-1/conversation")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }

    /* -------------------------------------------------
     * DELETE ALL MESSAGES BY USER
     * ------------------------------------------------- */
    @Test
    void deleteByUserKey_success() throws Exception {
        doNothing().when(messageBackupService).deleteByUserKey(userKey);

        mockMvc.perform(delete("/signal/backup/chat-messages")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));
    }
}
