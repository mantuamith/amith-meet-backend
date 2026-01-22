package com.algomeet.meetservice.service;

import com.algomeet.meetservice.config.AlgomeetJwtProps;
// ✅ Adjust this import to your actual Meeting class package:
import com.algomeet.meetservice.model.Meeting;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service

public class AlgomeetJwtService {

    @Autowired
    private  AlgomeetJwtProps props;

    public AlgomeetJwtService() {

    }

    /**
     * Build HS256 key from base64 secret.
     */
    private Key key() {
        byte[] raw = Base64.getDecoder().decode(
                Objects.requireNonNull(props.getSecretBase64(), "algomeet.jwt.secret-base64 required"));
        return Keys.hmacShaKeyFor(raw);
    }

    /**
     * Canonical generator binding a token to a specific room.
     * Puts moderator flag under context.user and sdkversion=1 at the context root.
     */
    public JwtBundle generateForRoom(
            String room,         // Jitsi/Algomeet room, e.g., meeting UUID or personal_room_id
            String userId,       // stable user key (UUID/SID)
            String displayName,  // optional
            String email,        // optional
            boolean moderator
    ) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getTtlSeconds());
        String jti = UUID.randomUUID().toString();

        // ---- context.user ----
        Map<String, Object> userCtx = new LinkedHashMap<>();
        userCtx.put("id", userId);
        if (displayName != null && !displayName.isBlank()) userCtx.put("name", displayName);
        if (email != null && !email.isBlank())             userCtx.put("email", email);
        userCtx.put("affiliation", moderator ? "owner" : "member");

        // ---- context ----
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user", userCtx);
        context.put("sdkversion", 1);

        // ---- JWT ----
        String token = Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setAudience(props.getAppId())     // aud
                .setIssuer(props.getIssuer())      // iss
                .setSubject(props.getSub())        // sub (meet domain/vhost/tenant)
                .claim("room", room)               // bind to one room
                .claim("context", context)         // context.user + sdkversion
                .setId(jti)                        // jti for revocation/single-use
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();

        return new JwtBundle(token, room, exp, jti);
    }

    /**
     * Convenience overload preserved for existing callers that pass a Meeting entity.
     * Delegates to generateForRoom(...) and adapts the return type to the legacy record.
     */
    public GeneratedAlgomeetToken generateForMeeting(
            Meeting meeting,
            String userKey,
            String displayName,
            String email,
            boolean moderator
    ) {
        // TODO: Revert hardcoded Room logic for fixing JWT
        // Right now meeting id and jwt room id needs to be same
        String room = meeting.getId();
        JwtBundle b = generateForRoom(room, userKey, displayName, email, moderator);
        return new GeneratedAlgomeetToken(b.token(), b.room(), b.exp(), b.jti());
    }

    /** Preferred return type for new code paths. */
    public record JwtBundle(
            String token,
            String room,
            Instant exp,
            String jti
    ) {}

    /** Legacy return type kept for backward compatibility with existing callers. */
    public record GeneratedAlgomeetToken(
            String token,
            String room,
            Instant exp,
            String jti
    ) {}
}
