package com.algomeet.notificationservice.consumer.processor;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;
import org.springframework.web.socket.WebSocketSession;

import com.algomeet.notificationservice.consumer.receiver.processor.ReceiverGroupProcessor;
import com.algomeet.notificationservice.consumer.receiver.processor.ReceiverGroupProcessorProvider;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.UserDto;
import com.algomeet.notificationservice.enums.DeviceType;
import com.algomeet.notificationservice.model.UserNotification;
import com.algomeet.notificationservice.publisher.PushMessagePublisher;
import com.algomeet.notificationservice.repository.UserNativeRepository;
import com.algomeet.notificationservice.repository.UserNotificationRepository;
import com.algomeet.notificationservice.service.ApnsSenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@ExtendWith(MockitoExtension.class)
class PushNotificationProcessorTest {

    @InjectMocks
    private PushNotificationProcessor processor;

    @Mock
    private UserNotificationRepository userNotificationRepository;

    @Mock
    private UserNativeRepository userNativeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ReceiverGroupProcessorProvider receiverGroupProcessorProvider;

    @Mock
    private ApnsSenderService apnsSenderService;

    @Mock
    private PushMessagePublisher pushMessagePublisher;

    @Mock
    private ObjectWriter objectWriter;

    @BeforeEach
    void setup() {

    }

    @Test
    void doProcess_shouldReturnEarly_whenNoReceiversAndNoGroup() {
        NotificationDto notification = new NotificationDto();
        notification.setReceiverIds(null);
        notification.setReceiverGroup(null);

        processor.doProcess(notification);

        verifyNoInteractions(
                userNotificationRepository,
                userNativeRepository,
                receiverGroupProcessorProvider,
                apnsSenderService,
                pushMessagePublisher
        );
    }

    @Test
    void doProcess_shouldPublishPushMessage_forNonIosUser() throws Exception {
        when(objectMapper.writer()).thenReturn(objectWriter);
        when(objectWriter.withDefaultPrettyPrinter()).thenReturn(objectWriter);
        
        UUID notifId = UUID.randomUUID();
        UUID userKey = UUID.randomUUID();

        NotificationDto notification = new NotificationDto();
        notification.setId(notifId);
        notification.setReceiverIds(Set.of(userKey.toString()));
        notification.setDeliveryAckRequired(false);

        UserDto user = new UserDto();
        user.setUserKey(userKey.toString());
        user.setDeviceType(DeviceType.ANDROID.name());

        when(userNativeRepository.getUsersByUserKeyList(any()))
                .thenReturn(List.of(user));
        when(receiverGroupProcessorProvider.getProcessors())
                .thenReturn(List.of());
        when(objectWriter.writeValueAsString(any()))
                .thenReturn("{json}");

        processor.doProcess(notification);

        verify(pushMessagePublisher).publish("{json}");
        verify(apnsSenderService, never()).sendPush(any(), any());
    }

    @Test
    void doProcess_shouldSendApnsPush_andUpdateDeliveryStatus() throws Exception {
        UUID notifId = UUID.randomUUID();
        UUID userKey = UUID.randomUUID();

        NotificationDto notification = new NotificationDto();
        notification.setId(notifId);
        notification.setReceiverIds(Set.of(userKey.toString()));
        notification.setDeliveryAckRequired(true);

        UserDto user = new UserDto();
        user.setUserKey(userKey.toString());
        user.setDeviceType(DeviceType.IOS.name());
        user.setDeviceToken("token123");

        UserNotification saved = new UserNotification();

        when(userNativeRepository.getUsersByUserKeyList(any()))
                .thenReturn(List.of(user));
        when(receiverGroupProcessorProvider.getProcessors())
                .thenReturn(List.of());
        when(userNotificationRepository.findByUserKeyAndNotification_Id(any(), any()))
                .thenReturn(List.of());
        when(userNotificationRepository.save(any()))
                .thenReturn(saved);
        when(apnsSenderService.sendPush(eq("token123"), any()))
                .thenReturn(true);

        processor.doProcess(notification);

        verify(apnsSenderService).sendPush(eq("token123"), any());
        verify(userNotificationRepository, times(2)).save(any());
    }

    @Test
    void doProcess_shouldSkipUser_whenNotificationAlreadyExists() throws Exception {
        UUID notifId = UUID.randomUUID();
        UUID userKey = UUID.randomUUID();

        NotificationDto notification = new NotificationDto();
        notification.setId(notifId);
        notification.setReceiverIds(Set.of(userKey.toString()));

        UserDto user = new UserDto();
        user.setUserKey(userKey.toString());
        user.setDeviceType(DeviceType.ANDROID.name());

        when(userNativeRepository.getUsersByUserKeyList(any()))
                .thenReturn(List.of(user));
        when(userNotificationRepository.findByUserKeyAndNotification_Id(any(), any()))
                .thenReturn(List.of(new UserNotification()));
        when(receiverGroupProcessorProvider.getProcessors())
                .thenReturn(List.of());

        processor.doProcess(notification);

        verifyNoInteractions(pushMessagePublisher);
        verify(apnsSenderService, never()).sendPush(any(), any());
    }

    @Test
    void doProcess_shouldCollectUsers_fromReceiverGroupProcessors() throws Exception {
        when(objectMapper.writer()).thenReturn(objectWriter);        
        when(objectWriter.withDefaultPrettyPrinter()).thenReturn(objectWriter);
        
        UUID userKey = UUID.randomUUID();

        NotificationDto notification = new NotificationDto();
        notification.setId(UUID.randomUUID());
        notification.setReceiverIds(Set.of("xxx-x"));

        UserDto user = new UserDto();
        user.setUserKey(userKey.toString());
        user.setDeviceType(DeviceType.ANDROID.name());

        ReceiverGroupProcessor groupProcessor = mock(ReceiverGroupProcessor.class);
        when(groupProcessor.getUserList(notification))
                .thenReturn(List.of(user));
        when(receiverGroupProcessorProvider.getProcessors())
                .thenReturn(List.of(groupProcessor));
        when(objectWriter.writeValueAsString(any()))
                .thenReturn("{json}");

        processor.doProcess(notification);

        verify(pushMessagePublisher).publish("{json}");
    }

    @Test
    void doProcess_shouldNotFail_whenApnsThrowsException() throws Exception {
        UUID userKey = UUID.randomUUID();

        NotificationDto notification = new NotificationDto();
        notification.setId(UUID.randomUUID());
        notification.setReceiverIds(Set.of(userKey.toString()));

        UserDto user = new UserDto();
        user.setUserKey(userKey.toString());
        user.setDeviceType(DeviceType.IOS.name());
        user.setDeviceToken("token");

        when(userNativeRepository.getUsersByUserKeyList(any()))
                .thenReturn(List.of(user));
        when(receiverGroupProcessorProvider.getProcessors())
                .thenReturn(List.of());
        when(userNotificationRepository.findByUserKeyAndNotification_Id(any(), any()))
                .thenReturn(List.of());
        when(apnsSenderService.sendPush(any(), any()))
                .thenThrow(new RuntimeException("APNS error"));

        assertDoesNotThrow(() -> processor.doProcess(notification));
        verify(apnsSenderService).sendPush(any(), any());
    }
}
