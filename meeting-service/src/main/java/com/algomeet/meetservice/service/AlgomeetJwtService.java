
package com.algomeet.meetservice.service;

import com.algomeet.meetservice.config.AlgomeetJwtProps;
import com.algomeet.meetservice.model.Meeting;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AlgomeetJwtService {

    private final AlgomeetJwtProps props;

    private SecretKey key() {
        byte[] raw = Base64.getDecoder().decode(
                Objects.requireNonNull(props.getSecretBase64(), "algomeet.jwt.secret-base64 required"));
        return Keys.hmacShaKeyFor(raw);
    }

    public GeneratedAlgomeetToken generateForMeeting(
            Meeting meeting,
            String userKey,         // UUID string from your auth
            String displayName,     // e.g., "John Doe"
            String email,           // user email
            boolean moderator       // host => true, others => false
    ) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getTtlSeconds());
        String jti = UUID.randomUUID().toString();

        // Room name used by Algomeet (typically the meeting.id)
        String room = meeting.getId();

        Map<String, Object> userCtx = new LinkedHashMap<>();
        //Optional.ofNullable(displayName).orElseGet(() -> email != null ? email : "Guest")
        if ( !displayName.isBlank() || !displayName.isEmpty()){
            userCtx.put("name", displayName);
        }

        if (email != null)
            userCtx.put("email", email);

        userCtx.put("id", userKey);
        userCtx.put("moderator", moderator);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("user", userCtx);
        context.put("sdkversion", 1); // << requested

        String token = Jwts.builder()
                .setHeaderParam("typ", "JWT")
                .setAudience(props.getAppId())     // aud
                .setIssuer(props.getIssuer())      // iss
                .setSubject(props.getSub())        // sub (your meet domain/vhost)
                .claim("room", room)               // bind to one room
                .claim("context", context)         // context.user.*, sdkversion
                .setId(jti)                        // for revocation / single-instance
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();

        return new GeneratedAlgomeetToken(token, room, exp, jti);
    }

    public record GeneratedAlgomeetToken(String token, String room, Instant exp, String jti) {}
}
