package com.algomeet.contactservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.Key;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey key;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String method = request.getMethod();
        final String path = request.getRequestURI();
        final String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ") &&
                SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey((key))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String principalName = claims.get("username", String.class);
                if (principalName == null || principalName.isBlank()) {
                    principalName = claims.getSubject();
                }

                if (principalName != null && !principalName.isBlank()) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            principalName, null, Collections.emptyList());

                    // Build details safely (no Map.of with nulls)
                    Map<String, Object> details = new HashMap<>(4);
                    String userKey = claims.get("user_key", String.class);
                    if (userKey != null && !userKey.isBlank()) details.put("user_key", userKey);
                    String sid = claims.get("sid", String.class);
                    if (sid != null && !sid.isBlank()) details.put("sid", sid);
                    // always useful:
                    details.put("email", claims.getSubject());

                    auth.setDetails(details);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.info("JWT auth OK: user={} user_key={} sid={} {} {}",
                            safe(claims.getSubject()), safe(userKey), safe(sid), method, path);
                }

            } catch (ExpiredJwtException ex) {
                log.info("JWT expired: {} {}: {}", method, path, ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
            } catch (MalformedJwtException ex) {
                log.warn("JWT malformed: {} {}: {}", method, path, ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Malformed token");
            } catch (Exception ex) {
                log.warn("JWT parsing failed: {} {}: {}", method, path, ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safe(String v) {
        return isBlank(v) ? "-" : v;
    }
}
