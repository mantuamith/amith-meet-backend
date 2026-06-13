package com.algomeet.mediaservice.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "media.file")
@Data
public class AcceptedFileProperties {
    private Set<String> imageExtensions;
    private Set<String> videoExtensions;
    private Set<String> documentExtensions;
    private Set<String> audioExtensions;

    private long maxImageSize    = 20_971_520L;   // 20 MB default
    private long maxVideoSize    = 209_715_200L;  // 200 MB default
    private long maxAudioSize    = 52_428_800L;   // 50 MB default
    private long maxDocumentSize = 104_857_600L;  // 100 MB default
}