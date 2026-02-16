package com.algomeet.mediaservice.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StorageUsageResponse {

    private UUID userKey;

    private long mediaStorageUsed;
    private long mediaFileCount;

    private long chatStorageUsed;
    private long chatMessageCount;

    private long totalStorageUsed;

    private Instant lastUpdated;
}
