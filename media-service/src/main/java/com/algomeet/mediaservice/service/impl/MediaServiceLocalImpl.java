package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.Storage;
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

    @Override
    public MediaUploadResponse upload(
    		String userKey,
    		List<String> sharedWithUserKeys,
            MultipartFile file,
            String contentType,
            boolean encrypted
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
            
            userFile.setOwner(userKey);
            List<FileAccessEntry> accessControlList = new ArrayList<>();
            
            Set<String> permittedUserKeys = new HashSet<>();
            
            if (!CollectionUtils.isEmpty(sharedWithUserKeys)) {
            	sharedWithUserKeys.forEach(uKey -> {
            		permittedUserKeys.add(uKey);
            	});
            	
            	// Add permission to owner itself if list id not empty, else it is ordinary upload
                permittedUserKeys.add(userKey);
            }    
                        
            if (sharedWithUserKeys != null) {
	            for(String sharedWithUserKey : sharedWithUserKeys) {
	            	Set<FilePermission> permissions = new HashSet<>();
	            	permissions.add(FilePermission.DOWNLOAD);
	            	permissions.add(FilePermission.SHARE);
	            	permissions.add(FilePermission.VIEW);
	            	permissions.add(FilePermission.DELETE);
	            	
	            	Integer refCount = 1;
	            	accessControlList.add(new FileAccessEntry(sharedWithUserKey, refCount, permissions));
	            }
            }
            
            userFile.setAccessControlList(accessControlList);      
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
                    .downloadUrl("/media/" + mediaId)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to store media", e);
        }
    }
    
    /**
     * Download media file as Path
     *
     * @param mediaId the stored db
     * @return Path of the file
     */
    public Path download(String userKey, String mediaId) {
    	UserFileDocument file = userFileService.getFile(mediaId, userKey, FilePermission.DOWNLOAD);
    	
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
