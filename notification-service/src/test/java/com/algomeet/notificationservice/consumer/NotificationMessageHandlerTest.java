package com.algomeet.notificationservice.consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.consumer.processor.NotificationProcessor;
import com.algomeet.notificationservice.consumer.processor.NotificationProcessorProvider;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

@ExtendWith(MockitoExtension.class)
class NotificationMessageHandlerTest {

    @Mock
    private NotificationProcessorProvider processorProvider;

    @Mock
    private NotificationProcessor processor1;

    @Mock
    private NotificationProcessor processor2;

    @InjectMocks
    private NotificationMessageHandler handler;

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void handleMessage_shouldSwitchTenant_executeProcessors_andClearContext() throws Exception {
        // Arrange
        UUID notificationId = UUID.randomUUID();

        NotificationDto dto = NotificationDto.builder()
                .id(notificationId)
                .tenantId(42)
                .deliveryAckRequired(true)
                .build();

        String jsonMessage = mapper.writeValueAsString(dto);

        when(processorProvider.getProcessors())
                .thenReturn(List.of(processor1, processor2));

        try (MockedStatic<TenantContext> tenantContextMock = mockStatic(TenantContext.class)) {

            // Act
            handler.handleMessage(jsonMessage);

            // Assert
            tenantContextMock.verify(() -> TenantContext.switchTenantExplicitly(42));
            tenantContextMock.verify(TenantContext::clear);

            verify(processor1).doProcess(dto);
            verify(processor2).doProcess(dto);
        }
    }

    @Test
    void handleMessage_shouldReturnEarly_whenMessageIsInvalidJson_andLogError() {
        // Arrange
        String invalidJson = "{ invalid json }";

        // Attach ListAppender to logger
        Logger logger =
            (Logger) LoggerFactory.getLogger(NotificationMessageHandler.class);

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try (MockedStatic<TenantContext> tenantContextMock =
                     mockStatic(TenantContext.class)) {

            // Act
            assertDoesNotThrow(() -> handler.handleMessage(invalidJson));

            // Assert: no processors, no tenant context
            verifyNoInteractions(processorProvider);
            tenantContextMock.verifyNoInteractions();
        }

        // Assert: log.error was called
        List<ILoggingEvent> logs = listAppender.list;

        boolean hasErrorLog = logs.stream()
                .anyMatch(event ->
                        event.getLevel().toString().equals("ERROR") &&
                        event.getFormattedMessage()
                                .contains("Error convering message to object"));

        assertTrue(hasErrorLog, "Expected error log was not found");

        // Cleanup
        logger.detachAppender(listAppender);
    }

    @Test
    void handleMessage_shouldReturnEarly_whenParsedObjectIsNull() throws SQLException {
        // Arrange
        String nullJson = "null";

        try (MockedStatic<TenantContext> tenantContextMock = mockStatic(TenantContext.class)) {

            // Act
            handler.handleMessage(nullJson);

            // Assert
            verifyNoInteractions(processorProvider);
            tenantContextMock.verifyNoInteractions();
        }
    }
}
