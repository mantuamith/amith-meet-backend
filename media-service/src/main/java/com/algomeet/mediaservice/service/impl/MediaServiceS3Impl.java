package com.algomeet.mediaservice.service.impl;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import com.algomeet.mediaservice.service.MediaServiceS3;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;


@Slf4j
@Service
@AllArgsConstructor
public class MediaServiceS3Impl implements MediaServiceS3 {
    private final S3Client s3Client;
    private UserFileService userFileService;
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
            String filename = mediaId + "_" + file.getOriginalFilename();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucket())
                    .key(filename)
                    .contentType(
                        contentType != null ? contentType : file.getContentType()
                    )
                    .contentLength(file.getSize())
                    .metadata(Map.of(
                        "encrypted", String.valueOf(encrypted),
                        "originalFilename", file.getOriginalFilename()
                    ))
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            log.info("Media uploaded to s3://{}/{}", storageProperties.getS3().getBucket(), filename);            
            
            UserFileDocument userFile = new UserFileDocument();
            userFile.setId(mediaId);
            userFile.setFilename(filename);
            userFile.setContentType(contentType != null ? contentType : file.getContentType());
            userFile.setSize(file.getSize());
            userFile.setAbsolutePath(filename);
            userFile.setEncrypted(encrypted);            
            userFile.setOwner(userKey);
            if (autoExpire) {
            	userFile.setCleanupEligibleAt(
            			Instant.now().plus(Duration.ofHours(storageProperties.getUnsharedFileExpirationHours())));
            } else {
            	// Update user storage usage            	
            	StorageUsageAdjustmentRequest storageUsageAdjustment = new StorageUsageAdjustmentRequest();
            	storageUsageAdjustment.setMediaFileCountDelta(1L);
            	storageUsageAdjustment.setMediaStorageBytesDelta(file.getSize());
            	userStorageUsageService.adjustUsage(UUID.fromString(userKey), storageUsageAdjustment);
            }

            userFile.setStorage(Storage.S3.name());
                        
            userFileService.create(userFile);
            

            return MediaUploadResponse.builder()
                    .mediaId(mediaId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(
                        contentType != null ? contentType : file.getContentType()
                    )
                    .size(file.getSize())
                    .encrypted(encrypted)
                    .url("/media/" + mediaId)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload media to S3", e);
        }
    }
      
    public String getReadUrl(String userKey, String mediaId) {

        if (!StringUtils.hasText(mediaId)) {
            throw new RuntimeException("Media Id is required");
        }
        // ️Verify the user has read/get permission
        UserFileDocument fileDoc = userFileService.getFile(mediaId, userKey, FilePermission.READ);        

        //  Build S3 key (absolute path)
        String objectKey = fileDoc.getAbsolutePath();

        //  Build the GetObjectRequest
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(storageProperties.getS3().getBucket())
                .key(objectKey)
                .build();

        //  Create a pre-signed URL valid for 15 minutes
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(storageProperties.getS3().getRegion())) // ⚡ bucket region
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(storageProperties.getS3().getSigExpirationInMinutes()))
                .getObjectRequest(getObjectRequest)
                .build();

        String url = presigner.presignGetObject(presignRequest).url().toString();
        presigner.close(); // close the presigner

        // Return URL to client
        return url;
    }   
    
    public boolean deleteIfExists(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getS3().getBucket())
                    .key(objectKey)
                    .build());

            log.info("Deleted S3 object: s3://{}/{}", storageProperties.getS3().getBucket(), objectKey);
            return true;

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.warn("S3 object not found: {}", objectKey);
                return false;
            }
            throw e;
        }
    }
}
