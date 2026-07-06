package com.algomeet.common.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "common.redis.stream")
public class CommonRedisStreamProperties {    
    
    /**
     * Redis stream key for asynchronously cleaning up media belonging to deleted messages.
     */
    private String messageMediaDeleteEvents = "message-media-delete-events";
    
    
    /**
     * Redis stream key for asynchronously updating message backup retention.
     */
    private String messageBackupRetentionUpdateEvents = "message-backup-retention-update-events";
}