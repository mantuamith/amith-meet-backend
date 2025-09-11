package com.algomeet.chatservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Component

public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final Key secretKey;

    public JwtHandshakeInterceptor(@Value("${jwt.secret}") String jwtSecret) {
        // jwt.secret must be Base64-encoded and at least 256 bits for HS256
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) throws Exception {
        final String uri = request.getURI().toString();
        log.info("[WS HANDSHAKE] URI: {}", request.getURI());

        String token = extractTokenFromQuery(uri).orElse(null);
        if (token == null) {
            token = Optional.ofNullable(request.getHeaders().getFirst("Authorization"))
                    .filter(h -> h.startsWith("Bearer "))
                    .map(h -> h.substring(7))
                    .orElse(null);
        }

        if (token == null) {
            log.warn("[WS HANDSHAKE] Missing token");
            return false;
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userKey  = claims.get("user_key", String.class); // may be null on older tokens
            String username = claims.get("username", String.class);
            String email    = claims.getSubject();                   // sub = email (your auth-service)

            // Choose a stable display name for Principal#getName()
            String principalName = (username != null && !username.isBlank()) ? username : email;
            if (principalName == null || principalName.isBlank()) {
                log.warn("[WS HANDSHAKE] No username/subject in token");
                return false;
            }

            // Attach as a rich Principal
            attributes.put("principal", new StompUserPrincipal(userKey, principalName, email));
            return true;

        } catch (Exception e) {
            log.warn("[WS HANDSHAKE] Invalid token: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
        log.info("[WS HANDSHAKE] After handshake");
    }

    private Optional<String> extractTokenFromQuery(String uri) {
        int q = uri.indexOf('?');
        if (q < 0) return Optional.empty();
        String query = uri.substring(q + 1);
        return Arrays.stream(query.split("&"))
                .map(s -> s.split("=", 2))
                .filter(kv -> kv.length == 2 && kv[0].equals("token"))
                .map(kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8))
                .findFirst()
                .filter(s -> !s.isBlank());
    }
}
