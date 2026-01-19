package com.algomeet.notificationservice.websocket.processor;

import static org.mockito.Mockito.*;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.dto.PingMessage;
import com.algomeet.notificationservice.enums.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PingMessageProcessorTest {

    @InjectMocks
    private PingMessageProcessor processor;

    @Mock
    private ObjectMapper mapper;

    @Mock
    private WebSocketSession session;

    private PingMessage pingMessage;

    @BeforeEach
    void setup() {
        pingMessage = new PingMessage();
        pingMessage.setTimestamp(new Date(System.currentTimeMillis()));
    }

    @Test
    void doProcess_shouldReturnEarly_whenPayloadIsNull() {
        assertDoesNotThrow(() -> processor.doProcess(session, null));
        verifyNoInteractions(mapper);
    }

    @Test
    void doProcess_shouldReturnEarly_whenPayloadIsEmpty() {
        assertDoesNotThrow(() -> processor.doProcess(session, ""));
        verifyNoInteractions(mapper);
    }

    @Test
    void doProcess_shouldParsePingMessage_whenPayloadIsValidJson() throws Exception {
        String json = "{ \"timestamp\": " + pingMessage.getTimestamp() + " }";

        when(mapper.readValue(json, PingMessage.class)).thenReturn(pingMessage);

        assertDoesNotThrow(() -> processor.doProcess(session, json));

        verify(mapper).readValue(json, PingMessage.class);
    }

    @Test
    void doProcess_shouldLogError_whenPayloadIsInvalidJson() throws Exception {
        String invalidJson = "{ invalid json }";

        when(mapper.readValue(invalidJson, PingMessage.class))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        assertDoesNotThrow(() -> processor.doProcess(session, invalidJson));

        verify(mapper).readValue(invalidJson, PingMessage.class);
    }

    @Test
    void getMessageType_shouldReturnPING() {
        assertEquals(MessageType.PING, processor.getMessageType());
    }
}
