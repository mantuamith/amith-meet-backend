package com.algomeet.meetservice.service;

import com.algomeet.meetservice.config.AlgomeetJwtProps;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlgomeetJwtServiceTest {

    @Test
    void generateForRoom_includesConfiguredFeaturesInContextClaim() {
        AlgomeetJwtProps props = new AlgomeetJwtProps();
        props.setAppId("algomeet");
        props.setIssuer("algomeet");
        props.setSub("meet.algomeet.com");
        props.setSecretBase64("YWxnb21lZXRfc2VjcmV0X2Jhc2U2NF9rZXlfMzJfYnl0ZXM=");
        props.setFeatures(Map.of(
                "file-upload", true,
                "screen-share", false
        ));

        AlgomeetJwtService service = new AlgomeetJwtService();
        ReflectionTestUtils.setField(service, "props", props);

        AlgomeetJwtService.JwtBundle jwt = service.generateForRoom(
                "room-1",
                "user-1",
                "Host Name",
                "host@example.com",
                true,
                Duration.ofMinutes(5)
        );

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        java.util.Base64.getDecoder().decode(props.getSecretBase64())))
                .build()
                .parseClaimsJws(jwt.token())
                .getBody();

        assertThat(claims.getAudience()).isEqualTo("algomeet");
        assertThat(claims.get("room", String.class)).isEqualTo("room-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> context = claims.get("context", Map.class);
        assertThat(context).containsKey("features");

        @SuppressWarnings("unchecked")
        Map<String, Object> features = (Map<String, Object>) context.get("features");
        assertThat(features)
                .containsEntry("file-upload", true)
                .containsEntry("screen-share", false);
    }
}
