package com.algomeet.meetservice.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        System.out.println(">>> JwtAuthenticationFilter hit");
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userIdOrEmail = claims.getSubject();
                System.out.println(">>> JWT parsed, subject: " + userIdOrEmail);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userIdOrEmail, null, Collections.emptyList());
                //authentication.setAuthenticated(true);
                System.out.println("[JWT DEBUG] Expiration: " + claims.getExpiration());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                System.out.println("[JWT DEBUG] Authenticated user email: " + userIdOrEmail);

            } catch (Exception e) {
                System.err.println("[JWT ERROR] Token verification failed: " + e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @PostConstruct
    public void init() {
        System.out.println("[DEBUG] JwtAuthenticationFilter initialized");
    }
}
