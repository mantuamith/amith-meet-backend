package com.algomeet.notificationservice.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.config.LocalizationConfig;
import com.algomeet.notificationservice.dto.PushNotificationRequest;
import com.algomeet.notificationservice.enums.NotificationType;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.enums.ResponseCode;
import com.algomeet.notificationservice.publisher.NotificationStreamPublisher;
import com.algomeet.notificationservice.util.LoggedInUserUtil;
import com.algomeet.notificationservice.util.MessageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;


@WebMvcTest(controllers = NotificationController.class)
@ContextConfiguration(classes = NotificationController.class)
@EnableAutoConfiguration
@AutoConfigureMockMvc(addFilters = false)
@Import(LocalizationConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationStreamPublisher notificationPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageSource messageSource;

    private MockedStatic<LoggedInUserUtil> loggedInUserUtilMock;
    private MockedStatic<TenantContext> tenantContextMock;
    
    @Autowired
    private NotificationController controller;

    @BeforeEach
    void setup() {
        new MessageUtil(messageSource);

        loggedInUserUtilMock = Mockito.mockStatic(LoggedInUserUtil.class);
        tenantContextMock = Mockito.mockStatic(TenantContext.class);

        loggedInUserUtilMock.when(LoggedInUserUtil::getUsername)
                .thenReturn("sender-user");

        tenantContextMock.when(TenantContext::getCurrentTenant)
                .thenReturn(1); // ✅ FIX: Integer, not String
    }

    @AfterEach
    void tearDown() {
        loggedInUserUtilMock.close();
        tenantContextMock.close();
    }

    /* -------------------------------------------------
     * CREATE PUSH NOTIFICATION
     * ------------------------------------------------- */

    @Test
    void create_endpointDisabled_shouldReturn503() throws Exception {
        PushNotificationRequest request = new PushNotificationRequest();

        request.setId(UUID.randomUUID().toString()); // valid UUID
        request.setType(NotificationType.DIRECT_MESSAGE); // @NotNull
        request.setTitle("New Message");
        request.setBody("You have received a new direct message.");
        request.setSenderId("user-123"); // max 32 chars

        request.setReceiverIds(Set.of("user-456", "user-789")); // optional
        request.setReceiverGroup(ReceiverGroup.USER_FRIENDS);          // optional
        request.setReceiverGroupRefId("group-abc");             // max 32 chars

        request.setData(Map.of(
            "messageId", "msg-001",
            "chatId", "chat-123"
        ));

        request.setDeliveryAckRequired(true);
        request.setCreatedAt(Instant.now());
        request.setExpiredAt(Instant.now().plusSeconds(3600));
        request.setTenantId(1);
        
        ReflectionTestUtils.setField(
                controller,
                "isPushNotificationEndpointEnabled",
                false
        );
        mockMvc.perform(post("/notifications/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value(ResponseCode.USER_NOTIFICATION_ENDPOINT_DISABLED.name()));
    }

    @Test
    void create_success_shouldAutoPopulateFields() throws Exception {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setId(UUID.randomUUID().toString()); // valid UUID
        request.setType(NotificationType.DIRECT_MESSAGE); // @NotNull
        request.setTitle("New Message");
        request.setBody("You have received a new direct message.");
        request.setSenderId("user-123"); // max 32 chars

        request.setReceiverIds(Set.of("user-456", "user-789")); // optional
        request.setReceiverGroup(ReceiverGroup.USER_FRIENDS);          // optional
        request.setReceiverGroupRefId("group-abc");             // max 32 chars

        request.setData(Map.of(
            "messageId", "msg-001",
            "chatId", "chat-123"
        ));

        request.setDeliveryAckRequired(true);
        request.setCreatedAt(Instant.now());
        request.setExpiredAt(Instant.now().plusSeconds(3600));
        request.setTenantId(1);

        doNothing().when(notificationPublisher).publish(Mockito.anyString());

        ReflectionTestUtils.setField(
                controller,
                "isPushNotificationEndpointEnabled",
                true
        );
        
        mockMvc.perform(post("/notifications/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

        verify(notificationPublisher).publish(Mockito.anyString());
    }

    @Test
    void create_success_withProvidedFields() throws Exception {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setId(UUID.randomUUID().toString()); // valid UUID
        request.setType(NotificationType.DIRECT_MESSAGE); // @NotNull
        request.setTitle("New Message");
        request.setBody("You have received a new direct message.");
        request.setSenderId("user-123"); // max 32 chars

        request.setReceiverIds(Set.of("user-456", "user-789")); // optional
        request.setReceiverGroup(ReceiverGroup.USER_FRIENDS);          // optional
        request.setReceiverGroupRefId("group-abc");             // max 32 chars

        request.setData(Map.of(
            "messageId", "msg-001",
            "chatId", "chat-123"
        ));

        request.setDeliveryAckRequired(true);
        request.setCreatedAt(Instant.now());
        request.setExpiredAt(Instant.now().plusSeconds(3600));
        request.setTenantId(1);

        doNothing().when(notificationPublisher).publish(Mockito.anyString());

        mockMvc.perform(post("/notifications/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

        verify(notificationPublisher).publish(Mockito.anyString());
    }

    /* -------------------------------------------------
     * INTERNAL CREATE
     * ------------------------------------------------- */

    @Test
    void internalCreate_success_shouldDelegateToCreate() throws Exception {
        PushNotificationRequest request = new PushNotificationRequest();
        request.setId(UUID.randomUUID().toString()); // valid UUID
        request.setType(NotificationType.DIRECT_MESSAGE); // @NotNull
        request.setTitle("New Message");
        request.setBody("You have received a new direct message.");
        request.setSenderId("user-123"); // max 32 chars

        request.setReceiverIds(Set.of("user-456", "user-789")); // optional
        request.setReceiverGroup(ReceiverGroup.USER_FRIENDS);          // optional
        request.setReceiverGroupRefId("group-abc");             // max 32 chars

        request.setData(Map.of(
            "messageId", "msg-001",
            "chatId", "chat-123"
        ));

        request.setDeliveryAckRequired(true);
        request.setCreatedAt(Instant.now());
        request.setExpiredAt(Instant.now().plusSeconds(3600));
        request.setTenantId(1);

        doNothing().when(notificationPublisher).publish(Mockito.anyString());

        mockMvc.perform(post("/internal/notifications/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.name()));

        verify(notificationPublisher).publish(Mockito.anyString());
    }
}
