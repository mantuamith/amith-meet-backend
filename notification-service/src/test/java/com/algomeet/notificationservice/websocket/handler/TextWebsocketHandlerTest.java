package com.algomeet.notificationservice.websocket.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.consumer.processor.PushNotificationProcessor;
import com.algomeet.notificationservice.dto.ExchangeMessage;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.NotificationMessage;
import com.algomeet.notificationservice.dto.UserNotificationDto;
import com.algomeet.notificationservice.enums.MessageType;
import com.algomeet.notificationservice.service.UserNotificationService;
import com.algomeet.notificationservice.websocket.beans.WebsocketUser;
import com.algomeet.notificationservice.websocket.processor.WebSocketMessageProcessor;
import com.algomeet.notificationservice.websocket.processor.WebSocketMessageProcessorProvider;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TextWebsocketHandlerTest {

    @InjectMocks
    private TextWebsocketHandler handler;

    @Mock
    private WebSocketMessageProcessorProvider processorProvider;

    @Mock
    private UserNotificationService userNotificationService;

    @Mock
    private PushNotificationProcessor pushNotificationProcessor;

    @Mock
    private WebSocketMessageProcessor messageProcessor;

    @Mock
    private WebSocketSession session;

    @Mock
    private WebsocketUser websocketUser;

    private static final String USER_KEY = "user-123";

    @BeforeEach
    void setup() {
        TextWebsocketHandler.getSessions().clear();

    }

    /* -------------------------------------------------
     * CONNECTION ESTABLISHED
     * ------------------------------------------------- */

    @Test
    void afterConnectionEstablished_shouldAddSessionAndPushUndelivered() throws Exception {
        when(session.getPrincipal()).thenReturn(websocketUser);
        when(websocketUser.getUserKey()).thenReturn(USER_KEY);
        
        UserNotificationDto dto = mock(UserNotificationDto.class);
        NotificationDto notification = mock(NotificationDto.class);

        when(userNotificationService.getUndeliveredNotifications(USER_KEY))
                .thenReturn(List.of(dto));
        when(dto.getNotification()).thenReturn(notification);

        handler.afterConnectionEstablished(session);

        assertThat(TextWebsocketHandler.getSessions())
                .containsKey(USER_KEY);
        assertThat(TextWebsocketHandler.getSessions().get(USER_KEY))
                .contains(session);

        verify(pushNotificationProcessor).pushMessage(
                eq(USER_KEY),
                any(NotificationMessage.class)
        );
    }

    /* -------------------------------------------------
     * HANDLE TEXT MESSAGE
     * ------------------------------------------------- */

    @Test
    void handleTextMessage_validPayload_shouldInvokeProcessor() throws Exception {
        // Ensure the session returns the mocked websocketUser
        when(session.getPrincipal()).thenReturn(websocketUser);

        // Make sure tenantId is set
        when(websocketUser.getTenantId()).thenReturn(1);

        // Prepare a valid ExchangeMessage
        ExchangeMessage exchange = new ExchangeMessage();
        exchange.setType(MessageType.NOTIFICATION);

        String payload = new ObjectMapper().writeValueAsString(exchange);

        // Prepare processor map
        when(processorProvider.getProcessors())
                .thenReturn(Map.of(MessageType.NOTIFICATION, messageProcessor));

        // Mock static TenantContext
        try (MockedStatic<TenantContext> tenantMock = mockStatic(TenantContext.class)) {
            // Do nothing when switchTenantExplicitly is called (since it's void)
            tenantMock.when(() -> TenantContext.switchTenantExplicitly(1)).thenCallRealMethod(); // or doNothing()

            // Do nothing for clear()
            tenantMock.when(TenantContext::clear).thenCallRealMethod();

            // Call handler
            handler.handleTextMessage(session, new TextMessage(payload));

            // Verify processor executed
            verify(messageProcessor).doProcess(eq(session), eq(payload));

            // Verify TenantContext was called
            tenantMock.verify(() -> TenantContext.switchTenantExplicitly(1));
            tenantMock.verify(TenantContext::clear);
        }
    }

    @Test
    void handleTextMessage_emptyPayload_shouldDoNothing() throws Exception {   	
        handler.handleTextMessage(session, new TextMessage(""));

        verifyNoInteractions(processorProvider);
        verifyNoInteractions(messageProcessor);
    }

    @Test
    void handleTextMessage_unknownMessageType_shouldNotProcess() throws Exception {
        // Ensure the session returns the mocked websocketUser
        when(session.getPrincipal()).thenReturn(websocketUser);
        when(websocketUser.getTenantId()).thenReturn(1);

        // Prepare a valid ExchangeMessage
        ExchangeMessage exchange = new ExchangeMessage();
        exchange.setType(MessageType.NOTIFICATION);

        String payload = new ObjectMapper().writeValueAsString(exchange);

        // Processor map is empty
        when(processorProvider.getProcessors()).thenReturn(Map.of());

        // Mock static TenantContext for void methods
        try (MockedStatic<TenantContext> tenantMock = mockStatic(TenantContext.class)) {
            tenantMock.when(() -> TenantContext.switchTenantExplicitly(1)).thenCallRealMethod(); // or doNothing()
            tenantMock.when(TenantContext::clear).thenCallRealMethod();

            // Call the handler
            handler.handleTextMessage(session, new TextMessage(payload));

            // Verify no processor is invoked
            verifyNoInteractions(messageProcessor);

            // Optionally verify TenantContext methods called
            tenantMock.verify(() -> TenantContext.switchTenantExplicitly(1));
            tenantMock.verify(TenantContext::clear);
        }
    }

    /* -------------------------------------------------
     * CONNECTION CLOSED
     * ------------------------------------------------- */
    @Test
    void afterConnectionClosed_shouldRemoveSession() throws Exception {
        when(session.getPrincipal()).thenReturn(websocketUser);
        when(websocketUser.getUserKey()).thenReturn(USER_KEY);
        
        handler.afterConnectionEstablished(session);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(TextWebsocketHandler.getSessions())
                .doesNotContainKey(USER_KEY);
    }

    /* -------------------------------------------------
     * STATIC CLEANUP
     * ------------------------------------------------- */

    @Test
    void removeSession_shouldCleanupSessionsMap() {
        when(session.getPrincipal()).thenReturn(websocketUser);
        when(websocketUser.getUserKey()).thenReturn(USER_KEY);
        
        TextWebsocketHandler.getSessions()
                .put(USER_KEY, new HashSet<>(Set.of(session)));

        TextWebsocketHandler.removeSession(session);

        assertThat(TextWebsocketHandler.getSessions()).isEmpty();
    }
}
