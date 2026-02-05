package com.algomeet.mediaservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaUploadResponse {

    private String mediaId;
    private String originalFilename;
    private String contentType;
    private long size;
    private boolean encrypted;
    private String url;
}
