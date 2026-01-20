package com.algomeet.notificationservice.consumer.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.model.Notification;
import com.algomeet.notificationservice.service.NotificationService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class SaveNotificationProcessorTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SaveNotificationProcessor processor;

    @BeforeEach
    void setup() {
        // Simulate @Value injection
        ReflectionTestUtils.setField(
                processor,
                "notificationDefaultExpirationInDays",
                30
        );
    }

    @Test
    void doProcess_shouldSaveNotification_andGenerateExpiration_whenMissing() {
        // Arrange
        NotificationDto dto = NotificationDto.builder()
                .id(UUID.randomUUID())
                .deliveryAckRequired(true)
                .tenantId(1)
                .build();

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        // Act
        processor.doProcess(dto);

        // Assert
        verify(notificationService).create(captor.capture());

        Notification saved = captor.getValue();
        assertNotNull(saved.getExpiredAt());

        Instant now = Instant.now();
        Instant expectedMin = now.plus(29, ChronoUnit.DAYS);
        Instant expectedMax = now.plus(31, ChronoUnit.DAYS);

        assertTrue(
                saved.getExpiredAt().isAfter(expectedMin)
                        && saved.getExpiredAt().isBefore(expectedMax),
                "Expiration should be ~30 days from now"
        );
    }

    @Test
    void doProcess_shouldNotOverrideExpiredAt_whenAlreadyPresent() {
        // Arrange
        Instant existingExpiration = Instant.now().plus(5, ChronoUnit.DAYS);

        NotificationDto dto = NotificationDto.builder()
                .id(UUID.randomUUID())
                .deliveryAckRequired(true)
                .expiredAt(existingExpiration)
                .build();

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        // Act
        processor.doProcess(dto);

        // Assert
        verify(notificationService).create(captor.capture());
        assertEquals(existingExpiration, captor.getValue().getExpiredAt());
    }

    @Test
    void doProcess_shouldReturnEarly_whenDeliveryAckNotRequired() {
        // Arrange
        NotificationDto dto = NotificationDto.builder()
                .deliveryAckRequired(false)
                .build();

        // Act
        processor.doProcess(dto);

        // Assert
        verifyNoInteractions(notificationService);
    }

    @Test
    void doProcess_shouldLogError_whenExceptionOccurs() {
        // Arrange
        NotificationDto dto = NotificationDto.builder()
                .id(UUID.randomUUID())
                .deliveryAckRequired(true)
                .build();

        doThrow(new RuntimeException("DB failure"))
                .when(notificationService)
                .create(any());

        Logger logger =
                (Logger) LoggerFactory.getLogger(SaveNotificationProcessor.class);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // Act + Assert (must not throw)
        assertDoesNotThrow(() -> processor.doProcess(dto));

        // Assert log
        boolean hasErrorLog = appender.list.stream()
                .anyMatch(e ->
                        e.getLevel().toString().equals("ERROR") &&
                        e.getFormattedMessage()
                                .contains("Error saving notification"));

        assertTrue(hasErrorLog, "Expected error log not found");

        logger.detachAppender(appender);
    }
}
