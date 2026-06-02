package com.algomeet.mediaservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Response returned after a successful media upload")
public class MediaUploadResponse {

    @Schema(description = "Unique identifier for the uploaded media", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String mediaId;

    @Schema(description = "Original filename as submitted by the client", example = "photo_001.jpg")
    private String originalFilename;

    @Schema(description = "MIME content type of the stored file", example = "image/jpeg")
    private String contentType;

    @Schema(description = "File size in bytes", example = "204800")
    private long size;

    @Schema(description = "Whether the file was uploaded as an encrypted blob", example = "false")
    private boolean encrypted;

    @Schema(description = "Relative URL to access the file via GET /media/{mediaId}", example = "/media/3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private String url;

    // ── Media metadata ─────────────────────────────────────────────────────────

    @Schema(description = "Image width in pixels (images only; null for video/audio/documents)", example = "1920")
    private Integer mediaWidth;

    @Schema(description = "Image height in pixels (images only; null for video/audio/documents)", example = "1080")
    private Integer mediaHeight;

    @Schema(description = "Media duration in seconds (reserved for future server-side video extraction; currently null — client should supply via upload metadata)", example = "null", nullable = true)
    private Double durationSeconds;

    // ── Chat context ────────────────────────────────────────────────────────────

    @Schema(description = "Chat session / conversation this file is attached to. Null when uploaded outside of a chat context.", example = "conv_abc123", nullable = true)
    private String conversationId;
}
