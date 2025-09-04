package com.algomeet.authservice.config;

import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.session.SidCache;
import com.algomeet.authservice.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final SidCache sidCache;     // <— tiny cache that calls user-service /active-sid
    private static final AntPathMatcher PATHS = new AntPathMatcher();


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return PATHS.match("/auth/register/**", path)
                || PATHS.match("/auth/login/**", path)
                || PATHS.match("/auth/refresh", path)
                || PATHS.match("/auth/password/**", path)
                || PATHS.match("/actuator/**", path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // 1) Skip preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // 2) Only proceed if Bearer token present AND no auth already set
        if (authHeader != null && authHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = authHeader.substring(7);

            try {
                // 2a) Validate JWT
                if (!jwtUtil.isTokenValid(token)) {
                    unauthorized(response, ResponseCode.AUTH_SESSION_REVOKED); // or AUTH_LOGIN_FAILED if you prefer
                    return;
                }

                // 2b) Extract email + sid from token
                String email = jwtUtil.extractEmail(token);
                String tokenSid = jwtUtil.extractSid(token);

                if (email == null || tokenSid == null) {
                    unauthorized(response, ResponseCode.AUTH_SESSION_REVOKED);
                    return;
                }

                // 2c) Compare with server-authoritative SID
                String currentSid = sidCache.getCurrentSid(email);
                if (currentSid == null || !tokenSid.equals(currentSid)) {
                    log.warn("JWT SID mismatch: email={} tokenSid={} currentSid={}",
                            mask(email), tokenSid, currentSid);
                    unauthorized(response, ResponseCode.AUTH_SESSION_REVOKED);
                    return;
                }

                // 2d) All good → set Authentication (principal=email)
                var auth = new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ex) {
                log.error("JWT filter error: {}", ex.toString());
                unauthorized(response, ResponseCode.AUTH_SESSION_REVOKED);
                return;
            }
        }

        // 3) Continue filter chain
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse res, ResponseCode rc) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = """
            {"code":"%s","message":"%s"}
        """.formatted(rc.getCode(), rc.getDefaultMessage());
        res.getWriter().write(json);
    }

    private String mask(String e) {
        return e == null ? "null" : e.replaceAll("(^.).*(@.*$)","$1***$2");
    }
}
