package com.algomeet.mediaservice.dto;

import lombok.Data;

/**
 * All fields are optional.
 * Values represent DELTAS (can be negative).
 */
@Data
public class StorageUsageAdjustmentRequest {

    /* Media */
    private Long mediaStorageBytesDelta;
    private Long mediaFileCountDelta;

    /* Chat */
    private Long chatStorageBytesDelta;
    private Long chatMessageCountDelta;

    /**
     * Optional reference (mediaId, messageId, etc.)
     * Useful for audit/debugging.
     */
    private String referenceId;
}
