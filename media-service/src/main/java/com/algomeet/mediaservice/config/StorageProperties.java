package com.algomeet.mediaservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * Which storage to use for uploading files: "local" or "s3"
     */
    private String activeUploadStorage;

    private LocalStorage local = new LocalStorage();
    private S3Storage s3 = new S3Storage();

    @Data
    public static class LocalStorage {
        private String dir;
    }

    @Data
    public static class S3Storage {
        private String bucket;          // algomeet-demo
        private String region;          // ap-southeast-1
        private String endpoint;        // optional (MinIO, Wasabi)
        private boolean pathStyleAccess;
        private Integer downloadMaxDurationInMinutes;
    }
}
