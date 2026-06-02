package com.algomeet.mediaservice.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.UploadContext;

public interface MediaService {

    /**
     * Upload a single file and persist its metadata.
     *
     * @param userKey       authenticated user UUID string
     * @param file          multipart file
     * @param contentType   override MIME type (nullable)
     * @param encrypted     true when the payload is already encrypted by the client
     * @param autoExpire    true = mark for cleanup after TTL if never shared
     * @param conversationId chat session this file belongs to (nullable)
     * @param uploadContext MEDIA or CHAT — controls storage quota bucket
     */
    MediaUploadResponse upload(
            String userKey,
            MultipartFile file,
            String contentType,
            boolean encrypted,
            boolean autoExpire,
            String conversationId,
            UploadContext uploadContext
    );

    boolean deleteIfExists(String fileLocation) throws IOException;
}
