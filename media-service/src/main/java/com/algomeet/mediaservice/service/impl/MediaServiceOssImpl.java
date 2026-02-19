package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.UserFileService;
import com.aliyun.oss.OSS;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class MediaServiceOssImpl implements MediaServiceOss {

    private final OSS ossClient;
    private final UserFileService userFileService;
    private final StorageProperties storageProperties;
    private UserStorageUsageService userStorageUsageService;

    @Override
    public MediaUploadResponse upload(
            String userKey,
            MultipartFile file,
            String contentType,
            boolean encrypted,
            boolean autoExpire
    ) {
        try {
            String mediaId = UUID.randomUUID().toString();
            String objectKey = mediaId + "_" + file.getOriginalFilename();

            ossClient.putObject(
            		storageProperties.getOss().getBucket(),
                    objectKey,
                    file.getInputStream()
            );

            log.info("Media uploaded to oss://{}/{}", storageProperties.getOss().getBucket(), objectKey);

            // ---- DB metadata ----
            UserFileDocument userFile = new UserFileDocument();
            userFile.setId(mediaId);
            userFile.setFilename(objectKey);
            userFile.setContentType(contentType != null ? contentType : file.getContentType());
            userFile.setSize(file.getSize());
            userFile.setAbsolutePath(objectKey);
            userFile.setOwner(userKey);
            userFile.setStorage(Storage.OSS.name());
            userFile.setEncrypted(encrypted);
            if (autoExpire) {
            	userFile.setCleanupEligibleAt(
            			Instant.now().plus(Duration.ofHours(storageProperties.getUnsharedFileExpirationHours())));
            }else {
            	// Update user storage usage            	
            	StorageUsageAdjustmentRequest storageUsageAdjustment = new StorageUsageAdjustmentRequest();
            	storageUsageAdjustment.setMediaFileCountDelta(1L);
            	storageUsageAdjustment.setMediaStorageBytesDelta(file.getSize());
            	userStorageUsageService.adjustUsage(UUID.fromString(userKey), storageUsageAdjustment);
            }
            
            userFileService.create(userFile);

            return MediaUploadResponse.builder()
                    .mediaId(mediaId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(userFile.getContentType())
                    .size(file.getSize())
                    .encrypted(encrypted)
                    .url("/media/" + mediaId)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload media to OSS", e);
        }
    }

    // ---------------- READ (SIGNED URL) ----------------
    public String getReadUrl(String userKey, String mediaId) {

        UserFileDocument fileDoc =
                userFileService.getFile(mediaId, userKey, FilePermission.READ);

        String objectKey = fileDoc.getAbsolutePath();

        Date expiration = new Date(
                System.currentTimeMillis()
                        + storageProperties.getOss().getSigExpirationInMinutes() * 60_000L
        );

        URL signedUrl = ossClient.generatePresignedUrl(
        		storageProperties.getOss().getBucket(),
                objectKey,
                expiration
        );

        return signedUrl.toString();
    }

    // ---------------- DELETE ----------------
    public boolean deleteIfExists(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }

        try {
            boolean exists = ossClient.doesObjectExist(
            		storageProperties.getOss().getBucket(),
                    objectKey
            );

            if (!exists) {
                log.warn("OSS object not found: {}", objectKey);
                return false;
            }

            ossClient.deleteObject(
            		storageProperties.getOss().getBucket(),
                    objectKey
            );

            log.info("Deleted OSS object: oss://{}/{}",
            		storageProperties.getOss().getBucket(),
                    objectKey
            );

            return true;

        } catch (Exception e) {
            log.error("Failed to delete OSS object {}", objectKey, e);
            throw e;
        }
    }
}
