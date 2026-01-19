package com.algomeet.notificationservice.websocket.processor;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.dto.NotificationAckMessage;
import com.algomeet.notificationservice.dto.NotificationAckMessage.Status;
import com.algomeet.notificationservice.enums.MessageType;
import com.algomeet.notificationservice.service.UserNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@ExtendWith(MockitoExtension.class)
class AckMessageProcessorTest {

    @InjectMocks
    private AckMessageProcessor processor;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private UserNotificationService userNotificationService;

    @Mock
    private WebSocketSession session;

    private NotificationAckMessage ackMessage;

    @BeforeEach
    void setup() {
        ackMessage = new NotificationAckMessage();
        ackMessage.setNotificationId(123L);
        ackMessage.setStatus(Status.DELIVERED);
    }

    @Test
    void doProcess_shouldReturnEarly_whenPayloadIsNull() {
        assertDoesNotThrow(() -> processor.doProcess(session, null));
        verifyNoInteractions(mapper, userNotificationService);
    }

    @Test
    void doProcess_shouldReturnEarly_whenPayloadIsEmpty() {
        assertDoesNotThrow(() -> processor.doProcess(session, ""));
        verifyNoInteractions(mapper, userNotificationService);
    }

    @Test
    void doProcess_shouldMarkAsDelivered_whenPayloadIsValidJson() throws Exception {
        String json = "{ \"notificationId\": 123, \"status\": \"DELIVERED\" }";

        when(mapper.readValue(json, NotificationAckMessage.class)).thenReturn(ackMessage);

        processor.doProcess(session, json);

        verify(mapper).readValue(json, NotificationAckMessage.class);
        verify(userNotificationService).markAsDelivered(123L);
    }

    @Test
    void doProcess_shouldLogError_whenPayloadIsInvalidJson() throws Exception {
        String invalidJson = "{ invalid json }";

        when(mapper.readValue(invalidJson, NotificationAckMessage.class))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        assertDoesNotThrow(() -> processor.doProcess(session, invalidJson));

        verify(mapper).readValue(invalidJson, NotificationAckMessage.class);
        verifyNoInteractions(userNotificationService); // markAsDelivered should not be called
    }

    @Test
    void getMessageType_shouldReturnACK() {
        assertEquals(MessageType.ACK, processor.getMessageType());
    }
}
