package com.algomeet.chatservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GlobalCorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // allow all endpoints
                .allowedOrigins("*") // allow all origins (or restrict to specific ones)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
/**
 * auth-service behind a gateway-service, remove this CORS config and move it to the gateway (typically using Spring Cloud Gateway's RouteLocatorBuilder or CorsWebFilter).
 *
 * Let me know when you're ready for that setup. --
 */