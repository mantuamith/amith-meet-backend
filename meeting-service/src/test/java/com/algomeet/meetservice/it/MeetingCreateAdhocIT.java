package com.algomeet.meetservice.it;

import com.algomeet.meetservice.MeetingServiceApplication;
import com.algomeet.meetservice.client.ControlClient;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.scheduler.MeetingCleanupScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = MeetingServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("it")
/*@EnabledIfSystemProperty(named = "it.docker", matches = "true")*/
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "algomeet.cleanupScheduler.enabled=false",
})
class MeetingCreateAdhocIT extends AbstractRedisIT {

  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;

  @MockBean
  MeetingCleanupScheduler scheduler;

  @MockBean
  UserDirectoryClient userDirectoryClient;

  // If Feign client is being called during startup, mock that too:
  @MockBean
  ControlClient controlServiceClient;

  @Value("${jwt.secret}") // matches application-it.yml
  private String jwtSecret;

  private String api(String p) { return "http://localhost:" + port + p; }

  @Container
  static GenericContainer<?> redis =
          new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProps(DynamicPropertyRegistry r) {
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Test
  void createAdhoc_thenFetch() {
    String token = TestJwt.build(jwtSecret, "host@example.com", 0); // tenantId=0 to match logs

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
    var email = "host@example.com";
    var user = new UserDirectoryClient.User(
            UUID.randomUUID(), "u1", email, "host", "Host",
            "0", /* personalRoom = */ null);

    when(userDirectoryClient.exact(email)).thenReturn(user);

    ResponseEntity<Map> create = rest.exchange(
            api("/api/meetings/create"), HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    if (!create.getStatusCode().is2xxSuccessful()) {
      System.out.println("=== RESPONSE STATUS: " + create.getStatusCode() + " ===");
      System.out.println("=== RESPONSE BODY ===");
      System.out.println(create.getBody());     // will show ProblemDetail / stack trace snippet
    }
    assertThat(create.getStatusCode()).isEqualTo(HttpStatus.OK);

    String id = (String) create.getBody().get("id");
    assertThat(id).isNotBlank();

    ResponseEntity<Map> get = rest.exchange(
            api("/api/meetings/" + id), HttpMethod.GET, new HttpEntity<>(h), Map.class);

    assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
