
package com.algomeet.groupservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class GlobalCorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:5173") // ✅ allow your Vue UI
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(false); // only true if you’re using cookies or auth headers
            }
        };
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
                "https://*.algoframe.in",
                "https://*.algomeet.app",
                "https://*.bhmeet.app"
                // any subdomain of algoframe.in
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



