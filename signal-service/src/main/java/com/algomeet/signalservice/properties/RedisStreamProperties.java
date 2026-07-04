package com.algomeet.signalservice.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "redis.stream")
public class RedisStreamProperties {

    /**
     * Redis stream key for storing purge message backup events.
     */
    private String purgeMessageBackup = "purge-message-backup-stream";
}