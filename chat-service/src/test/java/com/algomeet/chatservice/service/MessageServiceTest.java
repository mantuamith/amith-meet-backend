package com.algomeet.chatservice.service;

import com.algomeet.chatservice.client.GroupClient;
import com.algomeet.chatservice.document.GroupDto;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.dto.MessageStatusUpdate;
import com.algomeet.chatservice.dto.RecentReceivedMessageResponse;
import com.algomeet.chatservice.mapper.MessageMapper;
import com.algomeet.chatservice.repository.MessageRepository;
import com.algomeet.chatservice.sync.messaging.SimpMessagingSyncTemplate;
import com.algomeet.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private GroupClient groupClient;
    @Mock private SimpMessagingSyncTemplate messagingSyncTemplate;
    @Mock private MessageMapper messageMapper;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private NotificationService notificationService;

    @InjectMocks private MessageService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getRecentMessages_countsUnreadGroupMessagesPerUser() {
        GroupDto group = new GroupDto();
        group.setId("51");

        MessageDocument unreadFromOther = new MessageDocument();
        unreadFromOther.setId("g1");
        unreadFromOther.setSender("bob");
        unreadFromOther.setGroupId("51");
        unreadFromOther.setContent("hello");
        unreadFromOther.setTimestamp(Instant.parse("2026-04-09T09:00:00Z"));

        MessageDocument ownMessage = new MessageDocument();
        ownMessage.setId("g2");
        ownMessage.setSender("alice");
        ownMessage.setGroupId("51");
        ownMessage.setContent("mine");
        ownMessage.setTimestamp(Instant.parse("2026-04-09T09:01:00Z"));
        ownMessage.markReadBy("alice");

        MessageDocument alreadyRead = new MessageDocument();
        alreadyRead.setId("g3");
        alreadyRead.setSender("carol");
        alreadyRead.setGroupId("51");
        alreadyRead.setContent("seen");
        alreadyRead.setTimestamp(Instant.parse("2026-04-09T08:59:00Z"));
        alreadyRead.markReadBy("alice");

        when(messageRepository.findBySenderOrReceiver("alice", "alice")).thenReturn(List.of());
        when(groupClient.getGroupsForUsername("alice")).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInOrReceiverIn(Set.of("51"), Set.of("51")))
                .thenReturn(List.of(unreadFromOther, ownMessage, alreadyRead));

        List<RecentReceivedMessageResponse> recent = service.getRecentMessages("alice");

        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getContactId()).isEqualTo("51");
        assertThat(recent.get(0).getNMessages()).isEqualTo(1);
        assertThat(recent.get(0).getNewMessage()).isEqualTo("mine");
    }

    @Test
    void markMessagesAsRead_marksGroupMessageForReader() {
        GroupDto group = new GroupDto();
        group.setId("51");

        MessageDocument groupMessage = new MessageDocument();
        groupMessage.setId("g1");
        groupMessage.setSender("bob");
        groupMessage.setGroupId("51");
        groupMessage.setContent("hello");
        groupMessage.setTimestamp(Instant.parse("2026-04-09T09:00:00Z"));

        MessageStatusUpdate update = new MessageStatusUpdate();
        update.setMessageIds(List.of("g1"));
        update.setStatusTimeStamp(1_775_662_800L);

        when(messageRepository.findAllById(List.of("g1"))).thenReturn(List.of(groupMessage));
        when(messageRepository.findBySenderOrReceiver("alice", "alice")).thenReturn(List.of());
        when(groupClient.getGroupsForUsername("alice")).thenReturn(List.of(group));
        when(messageRepository.findByGroupIdInOrReceiverIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(groupMessage));

        service.markMessagesAsRead(update, "alice");

        assertThat(groupMessage.getReadByUsers()).contains("alice");
        verify(messageRepository).saveAll(List.of(groupMessage));
    }
}
