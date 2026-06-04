package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.enums.UploadContext;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.MediaMetadataExtractor;
import com.aliyun.oss.OSS;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
@ConditionalOnProperty(name = "storage.active-upload-storage", havingValue = "oss")
public class MediaServiceOssImpl implements MediaServiceOss {

    private final OSS ossClient;
    private final UserFileService userFileService;
    private final StorageProperties storageProperties;
    private UserStorageUsageService userStorageUsageService;
    private MediaMetadataExtractor metadataExtractor;

    @Override
    public MediaUploadResponse upload(
            String userKey,
            MultipartFile file,
            String contentType,
            boolean encrypted,
            boolean autoExpire,
            String conversationId,
            UploadContext uploadContext
    ) {
        try {
            String mediaId = UUID.randomUUID().toString();
            String objectKey = mediaId + "_" + file.getOriginalFilename();

            ossClient.putObject(storageProperties.getOss().getBucket(), objectKey, file.getInputStream());
            log.info("Media uploaded to oss://{}/{}", storageProperties.getOss().getBucket(), objectKey);

            MediaUploadResponse.MediaUploadResponseBuilder responseBuilder = MediaUploadResponse.builder()
                    .mediaId(mediaId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(contentType != null ? contentType : file.getContentType())
                    .size(file.getSize())
                    .encrypted(encrypted)
                    .url("/media/" + mediaId)
                    .conversationId(conversationId);

            metadataExtractor.populate(file, responseBuilder);
            MediaUploadResponse response = responseBuilder.build();

            UserFileDocument userFile = new UserFileDocument();
            userFile.setId(mediaId);
            userFile.setFilename(objectKey);
            userFile.setContentType(contentType != null ? contentType : file.getContentType());
            userFile.setSize(file.getSize());
            userFile.setAbsolutePath(objectKey);
            userFile.setOwner(userKey);
            userFile.setStorage(Storage.OSS.name());
            userFile.setEncrypted(encrypted);
            userFile.setConversationId(conversationId);
            userFile.setUploadContext(uploadContext != null ? uploadContext.name() : UploadContext.MEDIA.name());
            userFile.setMediaWidth(response.getMediaWidth());
            userFile.setMediaHeight(response.getMediaHeight());

            if (autoExpire) {
                userFile.setCleanupEligibleAt(
                        Instant.now().plus(Duration.ofHours(storageProperties.getUnsharedFileExpirationHours())));
            } else {
                adjustStorageUsage(userKey, file.getSize(), uploadContext);
            }

            userFileService.create(userFile);
            return response;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload media to OSS", e);
        }
    }

    public String getReadUrl(String userKey, String mediaId) {
        UserFileDocument fileDoc = userFileService.getFile(mediaId, userKey, FilePermission.READ);
        String objectKey = fileDoc.getAbsolutePath();

        Date expiration = new Date(
                System.currentTimeMillis()
                        + storageProperties.getOss().getSigExpirationInMinutes() * 60_000L
        );

        URL signedUrl = ossClient.generatePresignedUrl(
                storageProperties.getOss().getBucket(), objectKey, expiration);

        return signedUrl.toString();
    }

    public boolean deleteIfExists(String objectKey) {
        if (!StringUtils.hasText(objectKey)) return false;

        try {
            boolean exists = ossClient.doesObjectExist(storageProperties.getOss().getBucket(), objectKey);
            if (!exists) {
                log.warn("OSS object not found: {}", objectKey);
                return false;
            }
            ossClient.deleteObject(storageProperties.getOss().getBucket(), objectKey);
            log.info("Deleted OSS object: oss://{}/{}", storageProperties.getOss().getBucket(), objectKey);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete OSS object {}", objectKey, e);
            throw e;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void adjustStorageUsage(String userKey, long fileSize, UploadContext context) {
        StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
        if (context == UploadContext.CHAT) {
            req.setChatStorageBytesDelta(fileSize);
            req.setChatMessageCountDelta(1L);
        } else {
            req.setMediaStorageBytesDelta(fileSize);
            req.setMediaFileCountDelta(1L);
        }
        userStorageUsageService.adjustUsage(UUID.fromString(userKey), req);
    }
}
