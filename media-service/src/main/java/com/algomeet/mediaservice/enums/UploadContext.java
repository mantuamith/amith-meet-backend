package com.algomeet.mediaservice.enums;

/**
 * Determines how an uploaded file is counted against the owner's storage quota.
 * MEDIA  → counted as a general media file (mediaStorageUsed / mediaFileCount).
 * CHAT   → counted as a chat attachment (chatStorageUsed / chatMessageCount).
 */
public enum UploadContext {
    MEDIA,
    CHAT
}
