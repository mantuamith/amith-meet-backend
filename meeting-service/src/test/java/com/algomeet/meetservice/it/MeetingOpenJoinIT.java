package com.algomeet.meetservice.it;

import com.algomeet.meetservice.MeetingServiceApplication;
import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.model.RoomType;
import com.algomeet.meetservice.util.MeetingRoomIdAllocator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    classes = MeetingServiceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("it")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // link base used in MeetingMapper.joinUrl
        "algomeet.links.base=https://meet.algoframe.in",
        // JWT props used by AlgomeetJwtService
        "algomeet.jwt.secret-base64=YWxnb21lZXRfc2VjcmV0X2Jhc2U2NF9rZXlfMzJfYnl0ZXM=", // "algomeet_secret_base64_key_32_bytes" (base64)
        "algomeet.jwt.app-id=algomeet-app",
        "algomeet.jwt.issuer=meet-service",
        "algomeet.jwt.sub=meet.algoframe.in",
        "algomeet.jwt.ttl-seconds=300",
        // avoid accidental mail send / push
        "spring.mail.host=localhost",
})
@Slf4j
//@EnabledIfSystemProperty(named = "it.docker", matches = "true")
class MeetingOpenJoinIT extends AbstractRedisIT {

  @LocalServerPort
  int port;
  @Autowired
  TestRestTemplate rest;
  @Autowired
  MockMvc mvc;
  @Autowired
  ObjectMapper om;

  @MockBean
  UserDirectoryClient userDirectoryClient;
  @MockBean
  MeetingRoomIdAllocator roomAllocator;
  @MockBean
  JavaMailSender mailSender;

  private final String TENANT = "tenant-1";
  private final String HOST_EMAIL = "host@example.com";

  @BeforeEach
  void stubs() {
    var hostKey = UUID.randomUUID();

    when(userDirectoryClient.exact(anyString())).thenReturn(
            new UserDirectoryClient.User(
                    hostKey, "id-1", HOST_EMAIL, "host", "Host", TENANT, /*personal*/ null
            )
    );

    when(roomAllocator.allocateForTenant(TENANT)).thenReturn(
            Room.builder()
                    .roomId("119999999001")
                    .roomType(RoomType.ADHOC)
                    .tenantId(TENANT)
                    .ownerUserId(hostKey)
                    .ownerEmail(HOST_EMAIL)
                    .build()
    );
  }

  private String api(String p) { return "http://localhost:" + port + p; }

  @Test
  void openJoin_missingToken_400() {
    ResponseEntity<Map> r = rest.getForEntity(
        api("/api/meetings/open/241001000007"), java.util.Map.class);
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(r.getBody()).isNotNull();
    assertThat(r.getBody().get("code")).isEqualTo("TOKEN_REQUIRED");
  }

  @WithMockUser(username = HOST_EMAIL)
  void createWithPassword_thenJoinRequiresPassword_thenJoinOk() throws Exception {
    String body = """
            {
              "meetingName":"Secured",
              "meetingType":"MEETING",
              "meetingStartTime":"%s",
              "meetingEndTime":"%s",
              "usePersonalRoom": false,
              "passwordEnabled": true,
              "password": "abc123",
              "attendees": ["a@x.com","b@x.com"]
            }
        """.formatted(
            Instant.now().plusSeconds(600).toString(),
            Instant.now().plusSeconds(1800).toString()
    );

    // CREATE
    var create = mvc.perform(
                    post("/api/meetings/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(body))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.joinUrl").exists())
            .andExpect(jsonPath("$.passwordEnabled").value(true))
            .andReturn();

    String createJson = create.getResponse().getContentAsString();
    log.info("Create response: {}", createJson);
    JsonNode created = om.readTree(createJson);
    String meetingId = created.get("id").asText();
    String joinUrl = created.get("joinUrl").asText();
    String token = extractToken(joinUrl);

    // JOIN (no password) -> 403
    String joinNoPwd = """
            {
              "token": "%s",
              "password": ""
            }
        """.formatted(token);

    mvc.perform(
                    post("/api/meetings/open/{id}/join", meetingId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(joinNoPwd)
            ).andDo(print())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));

    // JOIN (correct password) -> 200 with algomeetToken & room
    String joinOk = """
            {
              "token": "%s",
              "password": "abc123",
              "name": "Guest X"
            }
        """.formatted(token);

    var joinOkRes = mvc.perform(
                    post("/api/meetings/open/{id}/join", meetingId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(joinOk)
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
            .andExpect(jsonPath("$.data.algomeetToken").exists())
            .andExpect(jsonPath("$.data.room").value("119999999001"))
            .andReturn();

    log.info("Join (ok) response: {}", joinOkRes.getResponse().getContentAsString());
  }

  @Test
  @WithMockUser(username = HOST_EMAIL)
  void createWithoutPassword_thenJoinOk() throws Exception {
    String body = """
            {
              "meetingName":"Open",
              "meetingType":"MEETING",
              "meetingStartTime":"%s",
              "meetingEndTime":"%s",
              "usePersonalRoom": false,
              "passwordEnabled": false
            }
        """.formatted(
            Instant.now().plusSeconds(600).toString(),
            Instant.now().plusSeconds(1800).toString()
    );

    // CREATE
    var create = mvc.perform(
                    post("/api/meetings/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(body))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.joinUrl").exists())
            .andExpect(jsonPath("$.passwordEnabled").value(false))
            .andReturn();

    String createJson = create.getResponse().getContentAsString();
    log.info("Create response: {}", createJson);

    JsonNode created = om.readTree(createJson);
    String meetingId = created.get("id").asText();
    String joinUrl = created.get("joinUrl").asText();
    String token = extractToken(joinUrl);

    // JOIN (no password provided, but not required) -> 200
    String join = """
            {
              "token": "%s",
              "name": "Guest Y"
            }
        """.formatted(token);

    var joinRes = mvc.perform(
                    post("/api/meetings/open/{id}/join", meetingId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(join))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("MEETING_JOINED_SUCCESS"))
            .andExpect(jsonPath("$.data.algomeetToken").exists())
            .andExpect(jsonPath("$.data.room").value("119999999001"))
            .andReturn();

    log.info("Join (no password) response: {}", joinRes.getResponse().getContentAsString());
  }

  private static String extractToken(String joinUrl) throws Exception {
    // joinUrl looks like: https://meet.algoframe.in/<id>?token=<uuid>
    var uri = new URI(joinUrl);
    String q = uri.getRawQuery(); // token=<...>
    assertThat(q).isNotBlank();
    for (String kv : q.split("&")) {
      String[] p = kv.split("=", 2);
      if (p.length == 2 && p[0].equals("token")) {
        return URLDecoder.decode(p[1], StandardCharsets.UTF_8);
      }
    }
    throw new IllegalStateException("token not found in joinUrl: " + joinUrl);
  }
}
