package com.algomeet.controlservice.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.algomeet.controlservice.enums.ResponseCode;
import com.algomeet.controlservice.util.JwtUtil;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return PATHS.match("/actuator/**", path)
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

                String username = jwtUtil.extractUsername(token);
                String userKey = jwtUtil.extractUserKey(token);
                String role = jwtUtil.extractRole(token);
                Integer tenantId = jwtUtil.extractTenantId(token);

                // 2d) All good → set Authentication (principal=email)
                List<GrantedAuthority> authorities = Collections.emptyList();
                if (role != null) {
                	authorities = List.of(new GrantedAuthority () {
            			@Override
            			public String getAuthority() {
            				if(!role.toUpperCase().startsWith("ROLE_")) {
            					return "ROLE_" + role;
            				}
            				return role;
            			}
            		});
                }

                @SuppressWarnings("serial")
				var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                auth.setDetails(Map.of(
                        "user_key", userKey,
                        "tenantId", tenantId != null ? String.valueOf(tenantId) : ""));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ex) {
                log.error("JWT filter error: {}", ex.toString(), ex);
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
