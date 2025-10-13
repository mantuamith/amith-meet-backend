package com.algomeet.meetservice.it;

import com.algomeet.meetservice.client.UserDirectoryClient;
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
import com.algomeet.meetservice.service.LinkFactory;
import com.algomeet.meetservice.service.MeetingService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


import java.time.Instant;
import java.util.Optional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;


import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MeetingController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, MeetingMapper.class, LinkFactory.class, LinkProps.class })
@AutoConfigureMockMvc(addFilters = true)
class MeetingControllerIT {

  @Autowired MockMvc mvc;

  @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
  @MockBean MeetingService meetingService;
  @MockBean AlgomeetJwtService algomeetJwtService;
  @MockBean AlgomeetMeetingTokenRegistry tokenRegistry;
  @MockBean
  UserDirectoryClient userDirectoryClient;

  @BeforeEach
  void passThroughJwt() throws Exception {
    doAnswer(inv -> { ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1)); return null; })
            .when(jwtAuthenticationFilter).doFilter(any(), any(), any());
  }

  private Meeting meeting(String id, String roomId, String hostEmail, String token, MeetingStatus status) {
    Meeting m = new Meeting();
    m.setId(id);
    m.setStatus(status);
    m.setHostEmail(hostEmail);
    m.setMeetingName("Daily");
    m.setToken(token);
    m.setMeetingStartTime(Instant.parse("2025-10-01T10:00:00Z"));
    m.setMeetingEndTime(Instant.parse("2025-10-01T10:30:00Z"));
    m.setRoom(Room.builder().roomId(roomId).roomType(RoomType.ADHOC).tenantId("tenant-1").build());
    return m;
  }

  @Test
  void flow_notStarted_thenStarted_and_both_app_and_guest_join() throws Exception {
    final String MEETING_ID = "241001000100";
    final String ROOM_ID    = "130000000777";
    final String HOST       = "host@example.com";
    final String TOKEN      = "tok-abc";
    final String PARTICIPANT= "alice@example.com";
    final String GUEST_NAME = "Guest Bob";

    // 1) Not started yet -> code MEETING_NOT_STARTED (status 200 by design)
    when(meetingService.getOpenMeetingById(MEETING_ID, TOKEN))
            .thenReturn(Optional.of(meeting(MEETING_ID, ROOM_ID, HOST, TOKEN, MeetingStatus.SCHEDULED)));

    mvc.perform(get("/api/meetings/open/{id}", MEETING_ID).param("token", TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("MEETING_NOT_STARTED"));

    // 2) Now the meeting is STARTED (e.g., host started it elsewhere)
    when(tokenRegistry.getIfActive(eq(MEETING_ID), anyString())).thenReturn(Optional.empty());
    when(meetingService.getOpenMeetingById(MEETING_ID, TOKEN))
            .thenReturn(Optional.of(meeting(MEETING_ID, ROOM_ID, HOST, TOKEN, MeetingStatus.STARTED)));

    // App participant (logged in) joins -> mints JWT “jwt-app”
    when(algomeetJwtService.generateForMeeting(any(Meeting.class), anyString(), any(), isNull(), eq(false)))
            .thenReturn(new AlgomeetJwtService.GeneratedAlgomeetToken("jwt-app", ROOM_ID, Instant.now().plusSeconds(300), "jti-app"));

    mvc.perform(get("/api/meetings/open/{id}", MEETING_ID)
                    .param("token", TOKEN)
                    .with(user(PARTICIPANT)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
            .andExpect(jsonPath("$.data.room").value(ROOM_ID))
            .andExpect(jsonPath("$.data.algomeetToken").value("jwt-app"));

    // Guest-by-link (anonymous) joins -> mints JWT “jwt-guest”
    when(algomeetJwtService.generateForMeeting(any(Meeting.class), anyString(), any(), isNull(), eq(false)))
            .thenReturn(new AlgomeetJwtService.GeneratedAlgomeetToken("jwt-guest", ROOM_ID, Instant.now().plusSeconds(300), "jti-guest"));

    mvc.perform(get("/api/meetings/open/{id}", MEETING_ID)
                    .param("token", TOKEN)
                    .param("displayName", GUEST_NAME)) // controller currently ignores this name; that's okay
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
            .andExpect(jsonPath("$.data.room").value(ROOM_ID))
            .andExpect(jsonPath("$.data.algomeetToken").value("jwt-guest"));
  }
}


