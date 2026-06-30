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
    private Set<String> archiveExtensions;

    private long maxImageSize    = 83_886_080L;   // 80 MB
    private long maxVideoSize    = 83_886_080L;   // 80 MB
    private long maxAudioSize    = 83_886_080L;   // 80 MB
    private long maxDocumentSize = 83_886_080L;   // 80 MB
    private long maxArchiveSize  = 83_886_080L;   // 80 MB

    private int maxFilesPerUpload = 10;
}