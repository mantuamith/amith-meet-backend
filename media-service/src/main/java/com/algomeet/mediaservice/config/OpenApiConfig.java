package com.algomeet.mediaservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("File Sharing API")
                        .description("Management of the file sharing feature")
                        .version("1.0.0"))
                .addServersItem(new Server()
                        .url("https://your-server-here")
                        .description("Generated server url"))
                .components(new Components().addSecuritySchemes("HttpBearerKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Http request Authorization header")))
                .addSecurityItem(new SecurityRequirement().addList("HttpBearerKey"));
    }
}
