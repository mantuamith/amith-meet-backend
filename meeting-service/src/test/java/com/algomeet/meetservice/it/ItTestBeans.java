package com.algomeet.meetservice.it;

import com.algomeet.meetservice.client.UserDirectoryClient;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.model.RoomType;
import com.algomeet.meetservice.service.AlgomeetJwtService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@TestConfiguration
public class ItTestBeans {

  @Bean
  @Primary
  UserDirectoryClient userDirectoryClientStub() {
    // Always returns a deterministic host
    return email -> new UserDirectoryClient.User(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "u-1",
        email,
        "host",
        "Host Name",
        "tenant-1",
        Room.builder()
            .roomId("120000000001")
            .roomType(RoomType.PERSONAL)
            .tenantId("tenant-1")
            .ownerUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .ownerEmail(email)
            .createdAt(Instant.now())
            .build()
    );
  }



  @Bean
  @Primary
  AlgomeetJwtService jwtServiceDeterministic() {
    AlgomeetJwtService algomeetJwtService = new AlgomeetJwtService() {
      @Override
      public GeneratedAlgomeetToken generateForMeeting(
              Meeting m,
              String userKey,
              String displayName,
              String email,
              boolean moderator, Duration d
      ) {
        return new GeneratedAlgomeetToken("jwt-it-token", m.getRoom().getRoomId(),
                Instant.now().plusSeconds(300), "it-jti");
      }
    };
    return algomeetJwtService;
  }
}
