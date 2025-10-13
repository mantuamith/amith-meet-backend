// src/test/java/com/algomeet/meetservice/it/TestJwt.java
package com.algomeet.meetservice.it;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

public final class TestJwt {
  private TestJwt() {}

  public static String build(String base64Secret, String subject, int tenantId) {
    Key key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));

    Instant now = Instant.now();
    return Jwts.builder()
        .setHeaderParam("typ", "JWT")
        .setSubject(subject)
        .setIssuedAt(Date.from(now))
        .setExpiration(Date.from(now.plusSeconds(3600))) // 1h
        // add both common spellings in case JwtConstants differs
        .addClaims(Map.of(
            "tenantId", tenantId,
            "x-tenant-id", tenantId
        ))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }
}
