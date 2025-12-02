package com.algomeet.opaqueservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.algomeet.opaqueservice.security.filter.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                		.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                        		"/internal/**").permitAll()
                        
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
                        
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // Add JWT filter for EP other than Register and Login
        return http.build();
    }
}
