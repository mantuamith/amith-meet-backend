package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StorageUsageResponse {

    private UUID userKey;

    private long mediaStorageUsed;
    private long mediaFileCount;

    private long chatStorageUsed;
    private long chatMessageCount;

    private long totalStorageUsed;

    private Instant lastUpdated;
}
