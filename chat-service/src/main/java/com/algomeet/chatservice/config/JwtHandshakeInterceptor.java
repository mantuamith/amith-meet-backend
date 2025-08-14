package com.algomeet.chatservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Key;
import java.security.Principal;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Key secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String query = request.getURI().getQuery();
        log.info("[WS HANDSHAKE] URI: {}", request.getURI());

        if (query != null && query.contains("token=")) {
            String token = Arrays.stream(query.split("&"))
                    .filter(s -> s.startsWith("token="))
                    .findFirst()
                    .map(s -> s.substring("token=".length()))
                    .orElse(null);

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.getSubject();
                log.info("[WS HANDSHAKE] JWT Subject (username): {}", username);

                attributes.put("principal", (Principal) () -> username);
                return true;

            } catch (Exception e) {
                log.warn("[WS HANDSHAKE] Invalid token: {}", e.getMessage());
                return false;
            }
        }

        log.warn("[WS HANDSHAKE] Missing token in query params");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        log.info("[WS HANDSHAKE] After handshake");
    }
}
