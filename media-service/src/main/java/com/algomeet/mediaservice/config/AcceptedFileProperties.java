package com.algomeet.mediaservice.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "media.file")
@Data
public class AcceptedFileProperties {
    private Set<String> imageExtensions;
    private Set<String> videoExtensions;
    private Set<String> documentExtensions;
    private Set<String> audioExtensions;
}