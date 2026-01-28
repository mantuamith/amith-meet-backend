package com.algomeet.subscription.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Subscription Service API",
                version = "v1",
                description = "APIs for Subscription"
        )
)
public class OpenApiConfig {
}
