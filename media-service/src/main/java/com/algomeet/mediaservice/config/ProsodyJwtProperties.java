package com.algomeet.mediaservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "prosody.jwt")
public class ProsodyJwtProperties {

    private String publicKeyPath = "/etc/ssl/certs/public-key.pem";
    private String audience = "file-sharing";
    private String issuer = "prosody";
    private boolean enabled = true;
}
