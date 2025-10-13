package com.algomeet.meetservice.it;

import com.algomeet.meetservice.MeetingServiceApplication;
import com.algomeet.meetservice.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = MeetingServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("it")
class MeetingFlowIT extends AbstractRedisIT {

  @LocalServerPort int port;

  @Autowired TestRestTemplate rest;

  @Value("${jwt.secret}") // same as in MeetingCreateAdhocIT
  private String jwtSecret;

  @MockBean
  private UserDirectoryClient userDirectoryClient;

  private String api(String path) { return "http://localhost:" + port + path; }

  @Test
  void createAdhocMeeting_thenFetch() {
    // real, signed JWT the filter accepts
    String email = "host@example.com";
    String token = TestJwt.build(jwtSecret, email, 0); // tenant=0 to match filter expectations

    // user-directory lookup stub so service can resolve host/tenant
    when(userDirectoryClient.exact(email)).thenReturn(
            new UserDirectoryClient.User(
                    UUID.randomUUID(), "u1", email, "host", "Host", "0", /* personalRoom */ null)
    );

    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    h.setBearerAuth(token);

    String body = """
      {
        "meetingName":"Daily",
        "meetingType":"MEETING",
        "meetingStartTime":"2030-01-01T10:00:00Z",
        "meetingEndTime":"2030-01-01T10:30:00Z",
        "usePersonalRoom": false,
        "attendees": ["a@x.com","b@x.com"]
      }
      """;

    ResponseEntity<Map> create = rest.exchange(
            api("/api/meetings/create"), HttpMethod.POST, new HttpEntity<>(body, h), Map.class);

    assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(create.getBody()).isNotNull();
    assertThat(create.getBody().get("id")).isNotNull();

    // DTO now exposes `room` object (RoomDto), not `roomId.*`
    Map room = (Map) create.getBody().get("room");
    assertThat(room.get("roomId")).as("12-digit adhoc room id").isNotNull();

    String meetingId = (String) create.getBody().get("id");

    // Fetch as host
    ResponseEntity<Map> get = rest.exchange(
            api("/api/meetings/" + meetingId),
            HttpMethod.GET,
            new HttpEntity<>(h),
            Map.class);

    assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
