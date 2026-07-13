package com.algomeet.meetservice.service;

import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.enums.MeetingType;
import com.algomeet.meetservice.model.*;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.repository.RoomRepository;
import com.algomeet.meetservice.util.MeetingIdGenerator;
import com.algomeet.meetservice.util.MeetingRoomIdAllocator;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.notificationservice.dto.Notification;
import com.algomeet.notificationservice.service.NotificationService;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingServiceTest {

    @InjectMocks private MeetingService service;

    @Mock private MeetingRepository meetingRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private JavaMailSender mailSender;
    @Mock private MeetingIdGenerator idGen;
    @Mock private NotificationService notificationService;
    @Mock private UserDirectoryClient userDirectoryClient;
    @Mock private MeetingRoomIdAllocator meetingRoomIdAllocator;

    @Captor private ArgumentCaptor<Meeting> meetingCaptor;

    private UserDirectoryClient.User host;
    private UUID hostKey;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expirationMinutes", 60);

        hostKey = UUID.randomUUID();
        // record User(UUID userKey, String id, String email, String username, String displayName, String tenantId, Room personalRoom)
        host = new UserDirectoryClient.User(
                hostKey, "u-1", "host@example.com", "host",
                "Host Name", "tenant-1",
                Room.builder()
                        .roomId("120000000001")
                        .roomType(RoomType.PERSONAL)
                        .tenantId("tenant-1")
                        .ownerUserId(hostKey)
                        .ownerEmail("host@example.com")
                        .createdAt(Instant.now())
                        .build()
        );

        when(idGen.nextId()).thenReturn("241001000123");
        // JPA mimic
        lenient().when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private MeetingRequest baseReq(boolean usePersonalRoom) {
        var r = new MeetingRequest();
        r.setMeetingName("Daily Standup");
        r.setMeetDescription("short sync");
        r.setMeetingType(MeetingType.MEETING);
        r.setMeetingStartTime(Instant.now().plus(5, ChronoUnit.MINUTES));
        r.setMeetingEndTime(Instant.now().plus(25, ChronoUnit.MINUTES));
        r.setUsePersonalRoom(usePersonalRoom);
        r.setReminderEnabled(true);
        r.setReminderMinutes(10);
        r.setLobbyEnabled(true);
        r.setAttendees(List.of("a@ex.com", "b@ex.com"));
        return r;
    }

    @Test
    void createMeeting_adhoc_success() {
        when(userDirectoryClient.exact("host@example.com")).thenReturn(host);

        Room adhoc = Room.builder()
                .roomId("130000000042")
                .roomType(RoomType.ADHOC)
                .tenantId("tenant-1")
                .createdAt(Instant.now())
                .build();
        when(meetingRoomIdAllocator.allocateForTenant("tenant-1")).thenReturn(adhoc);

        Meeting saved = service.createMeeting("host@example.com", baseReq(false));

        verify(meetingRepository).save(meetingCaptor.capture());
        Meeting persisted = meetingCaptor.getValue();

        assertThat(saved.getId()).isEqualTo("241001000123");
        assertThat(saved.getRoom().getRoomId()).isEqualTo("130000000042");
        assertThat(saved.getHostEmail()).isEqualTo("host@example.com");
        assertThat(saved.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
        assertThat(saved.getAttendees()).containsExactlyInAnyOrder("a@ex.com", "b@ex.com");

        assertThat(persisted.getRoom().getRoomId()).isEqualTo("130000000042");
        verify(meetingRoomIdAllocator).allocateForTenant("tenant-1");
        verify(notificationService).sendPush(any(Notification.class));
    }

    @Test
    void createMeeting_personal_success() {
        when(userDirectoryClient.exact("host@example.com")).thenReturn(host);
        when(roomRepository.findById("120000000001")).thenReturn(Optional.empty());

        Meeting saved = service.createMeeting("host@example.com", baseReq(true));

        assertThat(saved.getRoom().getRoomId()).isEqualTo("120000000001");
        assertThat(saved.getRoom().getRoomType()).isEqualTo(RoomType.PERSONAL);
        verify(roomRepository).save(argThat(r ->
                r.getRoomId().equals("120000000001") &&
                r.getRoomType() == RoomType.PERSONAL &&
                r.getTenantId().equals("tenant-1")
        ));
        verify(meetingRoomIdAllocator, never()).allocateForTenant(anyString());
        verify(notificationService).sendPush(any(Notification.class));
    }

    @Test
    void createMeeting_endBeforeStart_throws() {
        MeetingRequest bad = baseReq(false);
        bad.setMeetingEndTime(bad.getMeetingStartTime().minusSeconds(60));

        assertThatThrownBy(() -> service.createMeeting("host@example.com", bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meetingEndTime");

        verifyNoInteractions(userDirectoryClient, meetingRoomIdAllocator, meetingRepository);
    }

    @Test
    void createMeeting_personalRequestedButMissing_throws() {
        var hostNoRoom = new UserDirectoryClient.User(
                hostKey, "u-1", "host@example.com", "host",
                "Host Name", "tenant-1", null
        );
        when(userDirectoryClient.exact("host@example.com")).thenReturn(hostNoRoom);

        assertThatThrownBy(() -> service.createMeeting("host@example.com", baseReq(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not have a personal room");

        verify(meetingRoomIdAllocator, never()).allocateForTenant(anyString());
        verify(meetingRepository, never()).save(any());
    }

    @Test
    void createMeeting_hostNotFound_returns404_whenNull() {
        when(userDirectoryClient.exact("host@example.com")).thenReturn(null);

        assertThatThrownBy(() -> service.createMeeting("host@example.com", baseReq(false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    // Spring 6: getStatusCode()
                    org.springframework.http.HttpStatusCode sc = rse.getStatusCode();
                    assert sc.equals(HttpStatus.NOT_FOUND);
                    assert "Host not found".equals(rse.getReason());
                });

        verify(meetingRepository, never()).save(any());
    }

    @Test
    void createMeeting_personalRequested_butMissing_throws() {
        var host = new UserDirectoryClient.User(
                UUID.randomUUID(), "id-1", "host@example.com", "host",
                "Host", "tenant-1", null);
        when(userDirectoryClient.exact("host@example.com")).thenReturn(host);

        assertThatThrownBy(() -> service.createMeeting("host@example.com", baseReq(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("personal room");

        verify(meetingRepository, never()).save(any());
    }

    @Test
    void createMeeting_adhoc_success_persistsRoomAndMeeting() {
        var hostKey = UUID.randomUUID();
        var host = new UserDirectoryClient.User(
                hostKey, "id-1", "host@example.com", "host",
                "Host", "tenant-1", null);
        when(userDirectoryClient.exact("host@example.com")).thenReturn(host);

        Room newRoom = Room.builder()
                .roomId("120000000007")
                .roomType(RoomType.ADHOC)
                .tenantId("tenant-1")
                .build();
        when(meetingRoomIdAllocator.allocateForTenant("tenant-1")).thenReturn(newRoom);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = service.createMeeting("host@example.com", baseReq(false));

        assertThat(m.getId()).isEqualTo("241001000123");
        assertThat(m.getRoom().getRoomId()).isEqualTo("120000000007");
        assertThat(m.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);

        verify(roomRepository).save(any(Room.class));
        verify(meetingRepository).save(any(Meeting.class));
    }

    @Test
    void createMeeting_personal_success_createsRoomRowIfMissing() {
        var hostKey = UUID.randomUUID();
        Room personal = Room.builder()
                .roomId("990000000001")
                .roomType(RoomType.PERSONAL)
                .tenantId("tenant-1")
                .ownerUserId(hostKey)
                .ownerEmail("host@example.com")
                .build();

        var host = new UserDirectoryClient.User(
                hostKey, "id-1", "host@example.com", "host",
                "Host", "tenant-1", personal);
        when(userDirectoryClient.exact("host@example.com")).thenReturn(host);

        when(roomRepository.findById("990000000001")).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meetingRepository.save(any(Meeting.class))).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = service.createMeeting("host@example.com", baseReq(true));

        assertThat(m.getRoom().getRoomId()).isEqualTo("990000000001");
        assertThat(m.getRoom().getRoomType()).isEqualTo(RoomType.PERSONAL);
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void getOpenMeetingById_started_allows() {
        Meeting stored = new Meeting();
        stored.setId("241001000005");
        stored.setStatus(MeetingStatus.STARTED);
        stored.setToken("join-abc");

        when(meetingRepository.findById("241001000005")).thenReturn(Optional.of(stored));

        Optional<Meeting> out = service.getOpenMeetingById("241001000005", "join-abc");
        assertThat(out).isPresent();
    }

    @Test
    void getOpenMeetingById_badToken_denies() {
        Meeting stored = new Meeting();
        stored.setId("241001000006");
        stored.setStatus(MeetingStatus.STARTED);
        stored.setToken("correct");

        when(meetingRepository.findById("241001000006")).thenReturn(Optional.of(stored));

        Optional<Meeting> out = service.getOpenMeetingById("241001000006", "wrong");
        assertThat(out).isEmpty();
    }

    @Test
    void markMeetingAsCompleted_onlyHost() {
        Meeting m = new Meeting();
        m.setId("m1");
        m.setHostEmail("host@example.com");

        when(meetingRepository.findById("m1")).thenReturn(Optional.of(m));

        assertThat(service.markMeetingAsCompleted("m1", "host@example.com")).isTrue();
        assertThat(service.markMeetingAsCompleted("m1", "other@example.com")).isFalse();
    }

    @Test
    void createMeeting_hostNotFound_returns404_whenFeign404() {
        when(userDirectoryClient.exact("host@example.com"))
                .thenThrow(new FeignException.NotFound(
                        "404 Not Found",
                        feignRequest(),
                        null, // body
                        Collections.emptyMap() // headers
                ));

        assertThatThrownBy(() -> service.createMeeting("host@example.com", baseReq(false)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assert rse.getStatusCode().equals(HttpStatus.NOT_FOUND);
                    assert "Host not found".equals(rse.getReason());
                });

        verify(meetingRepository, never()).save(any());
    }

    // --- helpers ---

    private Request feignRequest() {
        return Request.create(
                Request.HttpMethod.GET,
                "/internal/users/lookup/exact?q=host%40example.com",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
    }


}
