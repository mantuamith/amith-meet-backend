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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.algomeet.authservice.enums.ResponseCode.AUTH_SESSION_REVOKED;

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
            log.info("token =  {}",token);
            try {
                // 2a) Validate JWT
                if (!jwtUtil.isTokenValid(token)) {

                    unauthorized(response); // or AUTH_LOGIN_FAILED if you prefer

                    return;
                }

                // 2b) Extract email + sid from token
                String email = jwtUtil.extractEmail(token);
                String tokenSid = jwtUtil.extractSid(token);
                String userKey = jwtUtil.extractUserKey(token);
                String role = jwtUtil.extractRole(token);
                Integer tenantId = jwtUtil.extractTenantId(token);

                if (email == null || tokenSid == null) {

                    unauthorized(response);

                    return;
                }

                // 2c) Compare with server-authoritative SID
                String currentSid = sidCache.getCurrentSid(email);
                if (currentSid == null || !tokenSid.equals(currentSid)) {
                    log.warn("JWT SID mismatch: email={} tokenSid={} currentSid={}",
                            mask(email), tokenSid, currentSid);

                    unauthorized(response);

                    return;
                }

                // 2d) All good → set Authentication (principal=email)
                List<GrantedAuthority> authorities = Collections.emptyList();
                if (role != null) {                	
                	authorities = List.of(new GrantedAuthority () {
            			@Override
            			public String getAuthority() {
            				if (!role.toUpperCase().startsWith("ROLE_")) {
            					return "ROLE_" + role;
            				}
            				
            				return role;
            			}
            		});
                }
                        
                @SuppressWarnings("serial")
				var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                auth.setDetails(Map.of(
                        "user_key", userKey,
                        "sid", tokenSid,
                        "tenantId", tenantId));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception ex) {
                log.error("JWT filter error: {}", ex.toString());

                unauthorized(response);

                return;
            }
        }

        // 3) Continue filter chain
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        if (response.isCommitted()) return;
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("""
        {"code":"%s","message":"%s"}
        """.formatted(ResponseCode.AUTH_SESSION_REVOKED.getCode(), ResponseCode.AUTH_SESSION_REVOKED.getDefaultMessage()));
        response.getWriter().flush();
    }

    private String mask(String e) {
        return e == null ? "null" : e.replaceAll("(^.).*(@.*$)","$1***$2");
    }
}
