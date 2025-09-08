package com.algomeet.chatservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;



    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // remove "Bearer "

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String username = claims.get("username",String.class);
                String subjectEmail = claims.getSubject();
                String userKey = claims.get("user_key", String.class);

                String principal = (username != null && !username.isBlank())
                        ? username
                        : subjectEmail;

                if (principal != null && !principal.isBlank()) {
                    Collection<SimpleGrantedAuthority> authorities = extractAuthorities(claims);

                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    // Attach useful JWT data for controllers/services that need it
                    auth.setDetails(Map.of(
                            "user_key", userKey,
                            "email", subjectEmail
                    ));

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ex) {
                // Log token validation errors and continue
                System.err.println("JWT validation failed: " + ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private Collection<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
        // Support either "roles": ["ROLE_USER", ...] or "role": "ROLE_USER"
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        String single = claims.get("role", String.class);
        if (single != null && !single.isBlank()) {
            return List.of(new SimpleGrantedAuthority(single));
        }
        return Collections.emptyList();
    }
}
