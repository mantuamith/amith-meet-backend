package com.algomeet.contactservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/contacts/**").authenticated()
                        
                        // Swagger - start
                        .requestMatchers("/swagger-ui.html").permitAll() 
                        .requestMatchers("/swagger-ui/index.html").permitAll()   
                        .requestMatchers("/swagger-ui/swagger-ui.css").permitAll()  
                        .requestMatchers("/swagger-ui/index.css").permitAll()  
                        .requestMatchers("/swagger-ui/swagger-ui-bundle.js").permitAll()  
                        .requestMatchers("/swagger-ui/swagger-ui-standalone-preset.js").permitAll()  
                        .requestMatchers("/swagger-ui/swagger-initializer.js").permitAll()  
                        .requestMatchers("/swagger-ui/favicon-32x32.png").permitAll()  
                        .requestMatchers("/swagger-ui/favicon-16x16.png").permitAll()  
                        .requestMatchers("/v3/api-docs/swagger-config").permitAll() 
                        .requestMatchers("/v3/api-docs").permitAll() 
                        // Swagger - end  
                        .anyRequest().permitAll()
                )
                .exceptionHandling(x -> x
                        .authenticationEntryPoint((req, res, ex) -> {
                            if (!res.isCommitted()) {
                                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                res.setContentType("application/json");
                                res.getWriter().write("{\"code\":\"AUTH_SESSION_REVOKED\",\"message\":\"Unauthorized\"}");
                            }
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            if (!res.isCommitted()) {
                                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                res.setContentType("application/json");
                                res.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"Access Denied\"}");
                            }
                        })
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        //TODO: For Prod Allow/restrict only intended
        // Allowed origins
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "https://localhost:8080",
                "http://localhost:8080",   // all localhost ports
                "https://*.algoframe.in" // any subdomain of algoframe.in
        ));

        // Allowed methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allowed headers
        config.setAllowedHeaders(List.of("*"));

        // Allow cookies / Authorization headers if needed
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
