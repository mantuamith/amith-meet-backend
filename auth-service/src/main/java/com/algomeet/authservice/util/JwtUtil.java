package com.algomeet.authservice.util;
/**
 * TODO:Set up role-based authorization based on role claim
 */

import com.algomeet.authservice.dto.UserResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final Key secretKey;
    private final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours  TODO: Externalize this
    private final long REFRESH_EXPIRATION_TIME = 30L * 24 * 60 * 60 * 1000; // 30 days  TODO: Externalize this
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    public String generateToken(UserResponse user) {
        
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .claim("user_key", safeUserKey(user))
                .claim("tenantId", user.getTenantId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(UserResponse user, String sid) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("username", user.getUsername())
                .claim("role",    user.getRole())
                .claim("sid", sid)
                .claim("user_key", safeUserKey(user))
                .claim("tenantId", user.getTenantId())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractSid(String token) {
        return extractClaim(token, claims -> claims.get("sid", String.class));
    }

    public String generateRefreshToken(UserResponse user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("username", user.getUsername())
                .claim("id", user.getId())  // Useful for lookup if needed
                .claim("type", "refresh")
                .claim("user_key", safeUserKey(user))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(UserResponse user, String sessionId) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("username", user.getUsername())
                .claim("id", user.getId())
                .claim("type", "refresh")
                .claim("sid", sessionId)
                .claim("user_key", safeUserKey(user))
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(REFRESH_EXPIRATION_TIME)))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return "refresh".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }


    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Validate if token is expired or malformed
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            System.out.println("Token expired: " + e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            System.out.println("Invalid token: " + e.getMessage());
        }
        return false;
    }

    private String safeUserKey(UserResponse user) {
        // Avoid NPE if your UserResponse doesn't yet have userKey
        try {
            var uk = user.getUserKey();
            return uk == null ? null : uk.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public String extractUserKey(String token) {
        return extractClaim(token, claims -> claims.get("user_key", String.class));
    }
}
