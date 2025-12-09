package com.algomeet.meetservice.it;

import com.algomeet.meetservice.MeetingServiceApplication;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.model.RoomType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = MeetingServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("it")
@AutoConfigureMockMvc
class MeetingCreatePersonalIT extends AbstractRedisIT {

    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;

    @MockBean private UserDirectoryClient userDirectoryClient;
    // If your app pings other beans on startup, mock them here too:
    // @MockBean private MeetingCleanupScheduler scheduler;
    // @MockBean private ControlClient controlServiceClient;

    @Test
    void createPersonal_ok() throws Exception {
        var hostKey = UUID.randomUUID();

        // Personal room the FE/account already has
        Room personal = Room.builder()
                .roomId("110000000123")
                .roomType(RoomType.PERSONAL)
                .tenantId("tenant-1")
                .ownerUserId(hostKey)
                .ownerEmail("host@example.com")
                .build();

        when(userDirectoryClient.exact(anyString())).thenReturn(
                new UserDirectoryClient.User(
                        hostKey, "id-1", "host@example.com", "host",
                        "Host", "tenant-1", personal
                )
        );

        String body = """
            {
              "meetingName":"Standup",
              "meetingType":"MEETING",
              "meetingStartTime":"%s",
              "meetingEndTime":"%s",
              "usePersonalRoom": true
            }
        """.formatted(
                Instant.now().plusSeconds(600).toString(),
                Instant.now().plusSeconds(1800).toString()
        );

        mvc.perform(post("/api/meetings/create")
                        .with(user("host@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // 🔁 JSON now has `room` (RoomDto), not `roomId.*`
                .andExpect(jsonPath("$.room.roomType").value("PERSONAL"))
                .andExpect(jsonPath("$.room.roomId").value("110000000123"))
                .andExpect(jsonPath("$.hostEmail").value("host@example.com"));
    }
}
