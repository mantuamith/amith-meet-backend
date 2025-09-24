package com.algomeet.authservice.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

public final class JwtTestFactory {

  private JwtTestFactory() {}

  /** Build an already-expired HS256 JWT using a raw (non-Base64) secret string. */
  public static String expiredTokenFor(String subject, String rawSecret) {
    Key key = Keys.hmacShaKeyFor(rawSecret.getBytes(StandardCharsets.UTF_8));
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(Date.from(now.minusSeconds(7200)))
        .setExpiration(Date.from(now.minusSeconds(60)))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  /** Build an already-expired HS256 JWT using a Base64-encoded secret string. */
  public static String expiredTokenForB64(String subject, String base64Secret) {
    Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(Date.from(now.minusSeconds(7200)))
        .setExpiration(Date.from(now.minusSeconds(60)))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  /** Same as above but you pass a pre-built Key (handy if your JwtUtil exposes it). */
  public static String expiredTokenFor(String subject, Key key) {
    Instant now = Instant.now();
    return Jwts.builder()
        .setSubject(subject)
        .setIssuedAt(Date.from(now.minusSeconds(7200)))
        .setExpiration(Date.from(now.minusSeconds(60)))
        .signWith(key, SignatureAlgorithm.HS256)
        .compact();
  }

  /** Create a valid token and then “tamper” with the signature for negative tests. */
  public static String tamperedToken(String subject, String rawSecret) {
    String valid = expiredTokenFor(subject, rawSecret).replaceFirst("\\.[^.]+$", ".badSig");
    return valid;
  }
}
