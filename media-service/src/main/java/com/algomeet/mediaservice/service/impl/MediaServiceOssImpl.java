package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
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
    private final StorageProperties ossProps;

    @Override
    public MediaUploadResponse upload(
            String userKey,
            List<String> sharedWithUserKeys,
            MultipartFile file,
            String contentType,
            boolean encrypted
    ) {
        try {
            String mediaId = UUID.randomUUID().toString();
            String objectKey = mediaId + "_" + file.getOriginalFilename();

            ossClient.putObject(
                    ossProps.getOss().getBucket(),
                    objectKey,
                    file.getInputStream()
            );

            log.info("Media uploaded to oss://{}/{}", ossProps.getOss().getBucket(), objectKey);

            // ---- DB metadata ----
            UserFileDocument userFile = new UserFileDocument();
            userFile.setId(mediaId);
            userFile.setFilename(objectKey);
            userFile.setContentType(contentType != null ? contentType : file.getContentType());
            userFile.setSize(file.getSize());
            userFile.setAbsolutePath(objectKey);
            userFile.setOwner(userKey);
            userFile.setStorage(Storage.OSS.name());

            List<FileAccessEntry> acl = new ArrayList<>();

            if (!CollectionUtils.isEmpty(sharedWithUserKeys)) {
                for (String sharedUser : sharedWithUserKeys) {
                    acl.add(new FileAccessEntry(
                            sharedUser,
                            1,
                            Set.of(
                                    FilePermission.VIEW,
                                    FilePermission.DOWNLOAD,
                                    FilePermission.SHARE,
                                    FilePermission.DELETE
                            )
                    ));
                }
            }

            userFile.setAccessControlList(acl);
            userFileService.create(userFile);

            return MediaUploadResponse.builder()
                    .mediaId(mediaId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(userFile.getContentType())
                    .size(file.getSize())
                    .encrypted(encrypted)
                    .downloadUrl("/media/" + mediaId)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload media to OSS", e);
        }
    }

    // ---------------- DOWNLOAD (SIGNED URL) ----------------
    public String getDownloadUrl(String userKey, String mediaId) {

        UserFileDocument fileDoc =
                userFileService.getFile(mediaId, userKey, FilePermission.DOWNLOAD);

        String objectKey = fileDoc.getAbsolutePath();

        Date expiration = new Date(
                System.currentTimeMillis()
                        + ossProps.getOss().getDownloadMaxDurationInMinutes() * 60_000L
        );

        URL signedUrl = ossClient.generatePresignedUrl(
                ossProps.getOss().getBucket(),
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
                    ossProps.getOss().getBucket(),
                    objectKey
            );

            if (!exists) {
                log.warn("OSS object not found: {}", objectKey);
                return false;
            }

            ossClient.deleteObject(
                    ossProps.getOss().getBucket(),
                    objectKey
            );

            log.info("Deleted OSS object: oss://{}/{}",
                    ossProps.getOss().getBucket(),
                    objectKey
            );

            return true;

        } catch (Exception e) {
            log.error("Failed to delete OSS object {}", objectKey, e);
            throw e;
        }
    }
}
