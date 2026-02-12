package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.repository.UserStorageUsageRepository;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@AllArgsConstructor
public class MediaServiceLocalImpl implements MediaServiceLocal {
	private StorageProperties storageProperties;
	private UserFileService userFileService;
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
        	String storageDir = storageProperties.getLocal().getDir() + 
        			(storageProperties.getLocal().getDir().trim().endsWith("/") ? "" : "/");
        	
            Files.createDirectories(Paths.get(storageDir));

            String mediaId = UUID.randomUUID().toString();
            String filename = mediaId + "_" + file.getOriginalFilename();

            Path target = Paths.get(storageDir).resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            log.info("Media stored at {}", target);
            
            UserFileDocument userFile = new UserFileDocument();
            userFile.setId(mediaId);
            userFile.setFilename(filename);
            userFile.setContentType(contentType != null ? contentType : file.getContentType());
            userFile.setSize(file.getSize());
            userFile.setAbsolutePath(target.toUri().getPath());
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
 
            userFile.setStorage(Storage.LOCAL.name());
            
            
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
            throw new RuntimeException("Failed to store media", e);
        }
    }
    
    /**
     * READ media file as Path
     *
     * @param mediaId the stored db
     * @return Path of the file
     */
    public Path read(String userKey, String mediaId) {
    	UserFileDocument file = userFileService.getFile(mediaId, userKey, FilePermission.READ);
    	
        Path filePath = Paths.get(file.getAbsolutePath());
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new RuntimeException("File not found: " + file.getFilename());
        }
        
        return filePath;
    }
    
    public boolean deleteIfExists(String fileLocation) throws IOException {
    	return Files.deleteIfExists(Paths.get(fileLocation));
    }
}
