package com.algomeet.meetservice.controller;

import com.algomeet.meetservice.Dto.MeetingDto;
import com.algomeet.meetservice.Dto.MeetingRequest;
import com.algomeet.meetservice.Dto.RoomDto;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.config.JwtAuthenticationFilter;
import com.algomeet.meetservice.config.LinkProps;
import com.algomeet.meetservice.config.SecurityConfig;
import com.algomeet.meetservice.enums.MeetingType;
import com.algomeet.meetservice.exception.RestExceptionHandler;
import com.algomeet.meetservice.mapper.MeetingMapper;
import com.algomeet.meetservice.model.*;
import com.algomeet.meetservice.repository.MeetingRepository;
import com.algomeet.meetservice.security.AlgomeetMeetingTokenRegistry;
import com.algomeet.meetservice.service.AlgomeetJwtService;
import com.algomeet.meetservice.service.LinkFactory;
import com.algomeet.meetservice.service.MeetingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MeetingController.class)
@Import({ SecurityConfig.class,RestExceptionHandler.class, MeetingMapper.class, LinkFactory.class, LinkProps.class })
@AutoConfigureMockMvc(addFilters = true)
@TestPropertySource(properties = "algomeet.links.base=")
@Slf4j
class MeetingControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean
    JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private MeetingService meetingService;
    @MockBean private MeetingRepository meetingRepository;
    @MockBean private AlgomeetJwtService algomeetJwtService;
    @MockBean private AlgomeetMeetingTokenRegistry tokenRegistry;
    @MockBean
    UserDirectoryClient userDirectoryClient;

    //@MockBean private MeetingMapper meetingMapper;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(inv -> {
            HttpServletRequest req  = inv.getArgument(0);
            HttpServletResponse res = inv.getArgument(1);
            FilterChain chain       = inv.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void createMeeting_authenticated_ok_realMapper_buildsInviteUrl() throws Exception {
        // Arrange: a fully-populated Meeting so the real mapper can do its job
        Meeting m = new Meeting();
        m.setId("241001000001");
        m.setHostEmail("host@example.com");
        m.setMeetingName("Daily");
        m.setMeetingType(MeetingType.MEETING);
        m.setMeetingStartTime(Instant.parse("2025-10-01T10:00:00Z"));
        m.setMeetingEndTime(Instant.parse("2025-10-01T10:30:00Z"));
        m.setStatus(MeetingStatus.SCHEDULED);

        // IMPORTANT: token must be set so MeetingMapper -> LinkFactory builds inviteUrl
        m.setToken("tok-123");

        m.setRoom(Room.builder()
                .roomId("130000000042")
                .roomType(RoomType.ADHOC)
                .tenantId("tenant-1")
                .ownerEmail("host@example.com")
                .lobbyDefault(false)
                .recordingDefault(true)
                .build());

        when(meetingService.createMeeting(eq("host@example.com"), any(MeetingRequest.class)))
                .thenReturn(m);

        String body = """
      {
        "meetingName":"Daily",
        "meetingType":"MEETING",
        "meetingStartTime":"2025-10-01T10:00:00Z",
        "meetingEndTime":"2025-10-01T10:30:00Z",
        "usePersonalRoom": false
      }
      """;

        // Act
        MvcResult result = mvc.perform(post("/api/meetings/create")
                        .with(user("host@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // top-level fields from real mapper
                .andExpect(jsonPath("$.id").value("241001000001"))
                .andExpect(jsonPath("$.meetingType").value("MEETING"))
                .andExpect(jsonPath("$.hostEmail").value("host@example.com"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.meetingStartTime").value("2025-10-01T10:00:00Z"))
                .andExpect(jsonPath("$.meetingEndTime").value("2025-10-01T10:30:00Z"))
                .andExpect(jsonPath("$.meetingName").value("Daily"))
                // defaults from entity unless you set them
                .andExpect(jsonPath("$.lobbyEnabled").value(false))
                .andExpect(jsonPath("$.reminderEnabled").value(false))
                .andExpect(jsonPath("$.reminderMinutes").value(10))
                // nested room
                .andExpect(jsonPath("$.room.roomId").value("130000000042"))
                .andExpect(jsonPath("$.room.roomType").value("ADHOC"))
                .andExpect(jsonPath("$.room.ownerEmail").value("host@example.com"))
                .andExpect(jsonPath("$.room.lobbyDefault").value(false))
                .andExpect(jsonPath("$.room.recordingDefault").value(true))
                // link from real mapper (LinkFactory with empty base -> leading slash)
                .andExpect(jsonPath("$.joinUrl").value("/241001000001?token=tok-123"))
                // password flag is false unless set on entity
                .andExpect(jsonPath("$.passwordEnabled").value(false))
                .andReturn();

        // Optional: log pretty response for debugging
        String raw = result.getResponse().getContentAsString();
        try {
            String pretty = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(raw).toPrettyString();
            org.slf4j.LoggerFactory.getLogger(getClass()).info("BODY:\n{}", pretty);
        } catch (Exception ignore) {}
    }


    /*@Test
    void createMeeting_authenticated_ok() throws Exception {
        Meeting m = new Meeting();
        m.setId("241001000001");
        m.setHostEmail("host@example.com");
        m.setMeetingName("Daily");
        m.setMeetingType(MeetingType.MEETING);
        m.setMeetingStartTime(Instant.parse("2025-10-01T10:00:00Z"));
        m.setMeetingEndTime(Instant.parse("2025-10-01T10:30:00Z"));
        m.setStatus(MeetingStatus.SCHEDULED);
        m.setToken("tok-123");
        m.setRoom(Room.builder()
                .roomId("130000000042")
                .roomType(RoomType.ADHOC)
                .tenantId("tenant-1")
                .ownerEmail("host@example.com")
                .lobbyDefault(false)
                .recordingDefault(true)
                .build());

        when(meetingService.createMeeting(eq("host@example.com"), any(MeetingRequest.class)))
                .thenReturn(m);

// Stub mapper -> return a DTO with ALL fields populated
        when(meetingMapper.toDto(any(Meeting.class))).thenReturn(
                new MeetingDto(
                        "241001000001",          // id
                        "MEETING",               // meetingType
                        "host@example.com",      // hostEmail
                        "SCHEDULED",             // status
                        Instant.parse("2025-10-01T10:00:00Z"),
                        Instant.parse("2025-10-01T10:30:00Z"),
                        new RoomDto(
                                "130000000042",   // roomId
                                "ADHOC",          // roomType
                                "host@example.com",
                                false,            // lobbyDefault
                                true              // recordingDefault
                        ),
                        "Daily",                 // meetingName
                        "Standup sync",          // meetingDescription
                        true,                    // lobbyEnabled
                        true,                    // reminderEnabled
                        15,                      // reminderMinutes
                        java.util.List.of("a@x.com", "b@x.com"),           // attendees
                        java.util.List.of("a@x.com", "b@x.com", "c@x.com"),// invitedParticipants
                        "/241001000001?token=tok-123",                     // inviteUrl
                        true                     // passwordEnabled
                )
        );
        String body = """ 
                { "meetingName":"Daily",
                "meetingType":"MEETING",
                "meetingStartTime":"2025-10-01T10:00:00Z",
                "meetingEndTime":"2025-10-01T10:30:00Z",
                "usePersonalRoom": false }
                """;

        mvc.perform(post("/api/meetings/create")
                        .with(user("host@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // top-level
                .andExpect(jsonPath("$.id").value("241001000001"))
                .andExpect(jsonPath("$.meetingType").value("MEETING"))
                .andExpect(jsonPath("$.hostEmail").value("host@example.com"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.meetingStartTime").value("2025-10-01T10:00:00Z"))
                .andExpect(jsonPath("$.meetingEndTime").value("2025-10-01T10:30:00Z"))
                .andExpect(jsonPath("$.meetingName").value("Daily"))
                .andExpect(jsonPath("$.meetingDescription").value("Standup sync"))
                .andExpect(jsonPath("$.lobbyEnabled").value(true))
                .andExpect(jsonPath("$.reminderEnabled").value(true))
                .andExpect(jsonPath("$.reminderMinutes").value(15))
                // arrays
                .andExpect(jsonPath("$.attendees[0]").value("a@x.com"))
                .andExpect(jsonPath("$.attendees[1]").value("b@x.com"))
                .andExpect(jsonPath("$.invitedParticipants[0]").value("a@x.com"))
                .andExpect(jsonPath("$.invitedParticipants[1]").value("b@x.com"))
                .andExpect(jsonPath("$.invitedParticipants[2]").value("c@x.com"))
                // nested room
                .andExpect(jsonPath("$.room.roomId").value("130000000042"))
                .andExpect(jsonPath("$.room.roomType").value("ADHOC"))
                .andExpect(jsonPath("$.room.ownerEmail").value("host@example.com"))
                .andExpect(jsonPath("$.room.lobbyDefault").value(false))
                .andExpect(jsonPath("$.room.recordingDefault").value(true))
                // link + password flag
                .andExpect(jsonPath("$.joinUrl").value("/241001000001?token=tok-123"))
                .andExpect(jsonPath("$.passwordEnabled").value(true));

// optional: verify interactions
        verify(meetingService).createMeeting(eq("host@example.com"), any(MeetingRequest.class));
        verify(meetingMapper).toDto(any(Meeting.class));
    }*/

    @Test
    void getOpenMeeting_ok_mintsToken() throws Exception {
        Meeting m = new Meeting();
        m.setId("241001000005");
        m.setStatus(MeetingStatus.STARTED);
        m.setMeetingName("Town Hall");
        m.setToken("join-token-xyz"); // not returned, only used by service
        m.setRoom(Room.builder().roomId("130000000099").roomType(RoomType.ADHOC).tenantId("tenant-1").build());

        when(meetingService.getOpenMeetingById("241001000005", "abc")).thenReturn(Optional.of(m));
        when(tokenRegistry.getIfActive(eq("241001000005"), anyString())).thenReturn(Optional.empty());
        when(algomeetJwtService.generateForMeeting(eq(m), anyString(), any(), isNull(), eq(false)))
                .thenReturn(new AlgomeetJwtService.GeneratedAlgomeetToken(
                        "jwt-token", m.getRoom().getRoomId(), Instant.now().plusSeconds(300), "jti-1"
                ));

        mvc.perform(get("/api/meetings/open/241001000005")
                        .param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is("MEETING_JOINED_SUCCESS")))
                .andExpect(jsonPath("$.data.algomeetToken", is("jwt-token")))
                .andExpect(jsonPath("$.data.room", is("130000000099")));
    }

    @Test
    void getOpenMeeting_missingToken_badRequest() throws Exception {
        mvc.perform(get("/api/meetings/open/241001000007"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("TOKEN_REQUIRED")));
    }

    @Test
    void createMeeting_invalidTimes_bubbles500() throws Exception {
        // Service will throw IllegalArgumentException (your validation)
        when(meetingService.createMeeting(eq("host@example.com"), any(MeetingRequest.class)))
                .thenThrow(new IllegalArgumentException("meetingEndTime cannot be before meetingStartTime"));

        String bad = """
            {
              "meetingName":"Daily",
              "meetingType":"MEETING",
              "meetingStartTime":"2025-10-01T10:30:00Z",
              "meetingEndTime":"2025-10-01T10:00:00Z",
              "usePersonalRoom": false
            }
        """;

        mvc.perform(post("/api/meetings/create")
                        .with(user("host@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMeeting_personalRoomMissing_bubbles500() throws Exception {
        when(meetingService.createMeeting(eq("host@example.com"), any(MeetingRequest.class)))
                .thenThrow(new IllegalStateException("Host does not have a personal room yet"));

        String body = """
      {
        "meetingName":"Daily",
        "meetingType":"MEETING",
        "meetingStartTime":"2025-10-01T10:00:00Z",
        "meetingEndTime":"2025-10-01T10:30:00Z",
        "usePersonalRoom": true
      }
    """;

        mvc.perform(post("/api/meetings/create")
                        .with(user("host@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEETING_BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Host does not have a personal room yet"));
    }

    @Test
    void getOpenMeeting_wrongToken_forbidden() throws Exception {
        when(meetingService.getOpenMeetingById("241001000006", "bad")).thenReturn(Optional.empty());

        mvc.perform(get("/api/meetings/open/241001000006").param("token", "bad"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("MEETING_ACCESS_DENIED")));
    }

    @Test
    void getOpenMeeting_completed_gone() throws Exception {
        Meeting m = new Meeting();
        m.setId("241001000008");
        m.setStatus(MeetingStatus.COMPLETED);
        m.setRoom(Room.builder().roomId("120000000001").roomType(RoomType.ADHOC).tenantId("t1").createdAt(Instant.now()).build());
        when(meetingService.getOpenMeetingById("241001000008", "abc")).thenReturn(Optional.of(m));

        mvc.perform(get("/api/meetings/open/241001000008").param("token", "abc"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("MEETING_COMPLETED")));
    }

    @Test
    void getOpenMeeting_expired_gone() throws Exception {
        Meeting m = new Meeting();
        m.setId("241001000009");
        m.setStatus(MeetingStatus.EXPIRED);
        m.setRoom(Room.builder().roomId("120000000002").roomType(RoomType.ADHOC).tenantId("t1").createdAt(Instant.now()).build());
        when(meetingService.getOpenMeetingById("241001000009", "abc")).thenReturn(Optional.of(m));

        mvc.perform(get("/api/meetings/open/241001000009").param("token", "abc"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code", is("MEETING_EXPIRED")));
    }
}
