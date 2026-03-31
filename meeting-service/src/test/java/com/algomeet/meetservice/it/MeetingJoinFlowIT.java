package com.algomeet.meetservice.it;

import com.algomeet.meetservice.config.JwtAuthenticationFilter;
import com.algomeet.meetservice.config.LinkProps;
import com.algomeet.meetservice.config.SecurityConfig;
import com.algomeet.meetservice.controller.MeetingController;
import com.algomeet.meetservice.exception.RestExceptionHandler;
import com.algomeet.meetservice.mapper.MeetingMapper;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.MeetingStatus;
import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.model.RoomType;
import com.algomeet.meetservice.security.AlgomeetMeetingTokenRegistry;
import com.algomeet.meetservice.service.AlgomeetJwtService;
import com.algomeet.meetservice.service.MeetingService;
import com.algomeet.meetservice.service.LinkFactory;
import com.algomeet.meetservice.client.UserDirectoryClient;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MeetingController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, MeetingMapper.class, LinkFactory.class, LinkProps.class })
@AutoConfigureMockMvc(addFilters = true)
class MeetingJoinFlowIT {

    private static final Logger log = LoggerFactory.getLogger(MeetingJoinFlowIT.class);

    @Autowired
    MockMvc mvc;

    /* slice-mocked beans */
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean MeetingService meetingService;
    @MockBean AlgomeetJwtService algomeetJwtService;
    @MockBean AlgomeetMeetingTokenRegistry tokenRegistry;
    @MockBean UserDirectoryClient userDirectoryClient;

    @BeforeEach
    void passThroughJwt() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    /* ===== helpers ===== */

    private Meeting sched(String id, String roomId, String hostEmail, String token) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setStatus(MeetingStatus.SCHEDULED);
        m.setHostEmail(hostEmail);
        m.setMeetingName("Daily");
        m.setToken(token);
        m.setRoom(Room.builder()
                .roomId(roomId)
                .roomType(RoomType.ADHOC)
                .tenantId("tenant-1")
                .ownerEmail(hostEmail)
                .lobbyDefault(false)
                .recordingDefault(true)
                .build());
        return m;
    }

    private Meeting started(Meeting base) {
        base.setStatus(MeetingStatus.STARTED);
        return base;
    }

    /** makes a minimal unsigned JWT with moderator flag in payload for easy inspection */
    private String makeJwtWithModerator(boolean moderator) {
        String headerJson = """
        {"alg":"none","typ":"JWT"}
        """;
        String payloadJson = """
        {"moderator":%s}
        """.formatted(moderator);

        String header  = base64Url(headerJson);
        String payload = base64Url(payloadJson);
        return header + "." + payload + ".";
    }

    private String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private void logResponse(String title, String body) {
        log.info("=== {} ===\n{}", title, body == null || body.isBlank() ? "<empty>" : body);
    }

    /* ====================== TESTS ====================== */

    @Test
    void host_join_starts_meeting_and_returns_moderator_token() throws Exception {
        final String MEETING_ID = "241001010001";
        final String ROOM_ID    = "130000009999";
        final String HOST       = "host@example.com";
        final String TOKEN      = "tok-host-xyz";

        // Meeting initially scheduled
        Meeting scheduled = sched(MEETING_ID, ROOM_ID, HOST, TOKEN);
        when(meetingService.getMeetingById(eq(MEETING_ID), eq(HOST), isNull()))
                .thenReturn(Optional.of(scheduled));

        // startIfScheduledByHost will flip status and persist
        doAnswer(inv -> {
            Meeting m = inv.getArgument(0);
            m.setStatus(MeetingStatus.STARTED);
            return m;
        }).when(meetingService).startIfScheduledByHost(any(Meeting.class));

        // Directory lookup for host
        when(userDirectoryClient.exact(HOST)).thenReturn(
                new UserDirectoryClient.User(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        /* id         */ null,                // or HOST if your id is same as email
                        /* email      */ HOST,
                        /* username   */ "host",
                        /* display    */ "Host Dude",
                        /* tenantId   */ "tenant-1",
                        /* personal   */ null                 // or a Room if you want to simulate a personal room
                )
        );

        // Mint jwt with moderator:true
        String jwt = makeJwtWithModerator(true);
        when(algomeetJwtService.generateForMeeting(any(Meeting.class), anyString(), anyString(), isNull(), eq(true),null))
                .thenReturn(new AlgomeetJwtService.GeneratedAlgomeetToken(jwt, ROOM_ID, Instant.now().plusSeconds(300), "jti-host"));

        var result = mvc.perform(post("/api/meetings/{id}/join", MEETING_ID)
                        .with(user(HOST))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
                .andExpect(jsonPath("$.data.room").value(ROOM_ID))
                .andExpect(jsonPath("$.data.algomeetToken").value(jwt))
                .andReturn();

        logResponse("host_join_starts_meeting response", result.getResponse().getContentAsString());

        // (Optional) parse JWT payload and assert moderator=true
        String[] parts = jwt.split("\\.");
        String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        log.info("Host JWT payload: {}", decodedPayload);
        org.assertj.core.api.Assertions.assertThat(decodedPayload).contains("\"moderator\":true");
    }

    @Test
    void attendee_before_start_gets_not_started_no_token() throws Exception {
        final String MEETING_ID = "241001010002";
        final String ROOM_ID    = "130000009990";
        final String HOST       = "host@example.com";
        final String ALICE      = "alice@example.com";
        final String TOKEN      = "tok-abc";

        Meeting scheduled = sched(MEETING_ID, ROOM_ID, HOST, TOKEN);
        // As attendee, controller calls getMeetingById(id, alice, null)
        when(meetingService.getMeetingById(eq(MEETING_ID), eq(ALICE), isNull()))
                .thenReturn(Optional.of(scheduled));

        when(userDirectoryClient.exact(ALICE)).thenReturn(
                new UserDirectoryClient.User(
                        UUID.fromString("00000000-0000-0000-0000-0000000000AA"),
                        /* id         */ null,
                        /* email      */ ALICE,
                        /* username   */ "alice",
                        /* display    */ "Alice A",
                        /* tenantId   */ "tenant-1",
                        /* personal   */ null
                )
        );

        var result = mvc.perform(post("/api/meetings/{id}/join", MEETING_ID)
                        .with(user(ALICE))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEETING_NOT_STARTED"))
                .andExpect(jsonPath("$.data.algomeetToken").doesNotExist())
                .andReturn();

        logResponse("attendee_before_start response", result.getResponse().getContentAsString());

        // Ensure we did NOT mint anything
        verify(algomeetJwtService, never()).generateForMeeting(any(), anyString(), anyString(), any(), anyBoolean(), isNull());
    }

    @Test
    void attendee_after_start_gets_token() throws Exception {
        final String MEETING_ID = "241001010003";
        final String ROOM_ID    = "130000009980";
        final String HOST       = "host@example.com";
        final String ALICE      = "alice@example.com";

        Meeting started = started(sched(MEETING_ID, ROOM_ID, HOST, "tok-x"));
        when(meetingService.getMeetingById(eq(MEETING_ID), eq(ALICE), isNull()))
                .thenReturn(Optional.of(started));

        when(userDirectoryClient.exact(HOST)).thenReturn(
                new UserDirectoryClient.User(
                        UUID.fromString("00000000-0000-0000-0000-0000000000BB"),
                        /* id         */ null,
                        /* email      */ HOST,
                        /* username   */ "bob",
                        /* display    */ "Bob B",
                        /* tenantId   */ "tenant-1",
                        /* personal   */ null
                )
        );

        when(tokenRegistry.getIfActive(eq(MEETING_ID), anyString())).thenReturn(Optional.empty());

        String jwt = makeJwtWithModerator(false);
        when(algomeetJwtService.generateForMeeting(any(Meeting.class), anyString(), anyString(), isNull(), eq(false),isNull()))
                .thenReturn(new AlgomeetJwtService.GeneratedAlgomeetToken(jwt, ROOM_ID, Instant.now().plusSeconds(300), "jti-a"));

        var result = mvc.perform(post("/api/meetings/{id}/join", MEETING_ID)
                        .with(user(ALICE))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
                .andExpect(jsonPath("$.data.room").value(ROOM_ID))
                .andExpect(jsonPath("$.data.algomeetToken").value(jwt))
                .andReturn();

        logResponse("attendee_after_start response", result.getResponse().getContentAsString());
    }

    @Test
    void rejoin_reuses_token_no_new_mint() throws Exception {
        final String MEETING_ID = "241001010004";
        final String ROOM_ID    = "130000009970";
        final String HOST       = "host@example.com";
        final String ALICE      = "alice@example.com";

        Meeting started = started(sched(MEETING_ID, ROOM_ID, HOST, "tok-y"));
        when(meetingService.getMeetingById(eq(MEETING_ID), eq(ALICE), isNull()))
                .thenReturn(Optional.of(started));

        when(userDirectoryClient.exact(ALICE)).thenReturn(
                new UserDirectoryClient.User(
                        UUID.fromString("00000000-0000-0000-0000-0000000000AA"),
                        /* id         */ null,
                        /* email      */ ALICE,
                        /* username   */ "alice",
                        /* display    */ "Alice A",
                        /* tenantId   */ "tenant-1",
                        /* personal   */ null
                )
        );

        when(tokenRegistry.getIfActive(eq(MEETING_ID), anyString()))
                .thenReturn(Optional.of("t1"));

        var result = mvc.perform(post("/api/meetings/{id}/join", MEETING_ID)
                        .with(user(ALICE))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
                .andExpect(jsonPath("$.data.algomeetToken").value("t1"))
                .andReturn();

        logResponse("rejoin_reuses_token response", result.getResponse().getContentAsString());

        verify(algomeetJwtService, never()).generateForMeeting(any(), anyString(), anyString(), any(), anyBoolean(), isNull());
    }

    @Test
    void metadata_path_forbidden_for_attendee_when_completed() throws Exception {
        final String MEETING_ID = "241001010005";
        final String ROOM_ID    = "130000009960";
        final String HOST       = "host@example.com";
        final String BOB        = "bob@example.com";

        Meeting completed = sched(MEETING_ID, ROOM_ID, HOST, "tok-z");
        completed.setStatus(MeetingStatus.COMPLETED);

        // Controller will call getMeetingById(id, bob, null) → empty means 403
        when(meetingService.getMeetingById(eq(MEETING_ID), eq(BOB), isNull()))
                .thenReturn(Optional.empty());

        var result = mvc.perform(get("/api/meetings/{id}", MEETING_ID)
                        .with(user(BOB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEETING_ACCESS_DENIED"))
                .andReturn();

        logResponse("metadata_path_forbidden response", result.getResponse().getContentAsString());
    }
}
