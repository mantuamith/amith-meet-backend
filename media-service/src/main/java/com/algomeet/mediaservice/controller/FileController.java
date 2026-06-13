package com.algomeet.mediaservice.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.controller.swagger.FileControllerDoc;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.BatchMediaDeleteRequest;
import com.algomeet.mediaservice.dto.BatchMediaShareRequest;
import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.enums.UploadContext;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.MediaServiceS3;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.FileValidator;
import com.algomeet.mediaservice.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class FileController implements FileControllerDoc {

    @Value("${media.base-url}")
    private String mediaBaseUrl;

    private final MediaServiceLocal mediaServiceLocal;
    @Autowired(required = false)
    private MediaServiceS3 mediaServiceS3;
    @Autowired(required = false)
    private MediaServiceOss mediaServiceOss;
    private final StorageProperties storageProperties;
    private final UserFileService userFileService;
    private final FileValidator fileValidator;

    // ========================= UPLOAD (single) =========================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<MediaUploadResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Boolean encrypted,
            @RequestParam(required = false, defaultValue = "true") Boolean autoExpire,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false, defaultValue = "MEDIA") UploadContext uploadContext
    ) throws Exception {

        log.info("Uploading media: name={}, size={} bytes, context={}", file.getOriginalFilename(), file.getSize(), uploadContext);

        try {
            fileValidator.validate(file, encrypted != null && encrypted);
        } catch (FileTypeNotSupportedException ex) {
            log.error("File type not supported: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(CommonResponse.from(ResponseCode.MEDIA_FILE_TYPE_NOT_SUPPORTED));
        }

        MediaUploadResponse response = doUpload(SecurityUtil.getUserKey(), file, contentType,
                encrypted != null && encrypted, autoExpire != null && autoExpire, conversationId, uploadContext);

        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
    }

    // ========================= UPLOAD (batch) =========================

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<List<MediaUploadResponse>>> uploadBatch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) Boolean encrypted,
            @RequestParam(required = false, defaultValue = "true") Boolean autoExpire,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false, defaultValue = "CHAT") UploadContext uploadContext
    ) throws Exception {

        log.info("Batch upload: fileCount={}, context={}", files.size(), uploadContext);

        List<MediaUploadResponse> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                fileValidator.validate(file, encrypted != null && encrypted);
                MediaUploadResponse resp = doUpload(SecurityUtil.getUserKey(), file, null,
                        encrypted != null && encrypted, autoExpire != null && autoExpire,
                        conversationId, uploadContext);
                UserFileDocument fileDoc = userFileService.getFile(resp.getMediaId(), SecurityUtil.getUserKey(), null, FilePermission.READ);
                resp.setThumbnailUrl(resolveThumbnailUrl(fileDoc, resp.getMediaId()));
                results.add(resp);
            } catch (FileTypeNotSupportedException ex) {
                log.warn("Batch item rejected (unsupported type): {}", file.getOriginalFilename(), ex);
                failures.add(file.getOriginalFilename());
            } catch (org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
                // Rethrow — client must reduce file size, 413 returned by GlobalExceptionHandler
                throw ex;
            } catch (Exception ex) {
                log.error("Batch item failed: {}", file.getOriginalFilename(), ex);
                failures.add(file.getOriginalFilename());
            }
        }

        if (!failures.isEmpty() && results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(CommonResponse.from(ResponseCode.MEDIA_FILE_TYPE_NOT_SUPPORTED));
        }

        if (!failures.isEmpty()) {
            // Partial success
            return ResponseEntity.status(HttpStatus.MULTI_STATUS)
                    .body(CommonResponse.from(ResponseCode.MEDIA_BATCH_UPLOAD_PARTIAL_FAILURE, results));
        }

        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, results));
    }

    // ========================= GET/READ =========================

    @GetMapping("/{mediaId}")
    public ResponseEntity<?> getMedia(@PathVariable UUID mediaId,
    		@RequestParam(required = false) UUID groupId) {
        try {
            UserFileDocument fileDoc = userFileService.getFile(mediaId.toString(), SecurityUtil.getUserKey(), groupId, FilePermission.READ);

            return switch (Storage.valueOf(fileDoc.getStorage())) {
                case LOCAL -> {
                    Path filePath = mediaServiceLocal.read(SecurityUtil.getUserKey(), groupId, mediaId.toString());
                    String ct = Files.probeContentType(filePath);
                    if (ct == null) ct = MediaType.APPLICATION_OCTET_STREAM_VALUE;

                    InputStreamResource resource = new InputStreamResource(Files.newInputStream(filePath));
                    yield ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(ct))
                            .contentLength(Files.size(filePath))
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
                            .body(resource);
                }
                case S3 -> {
                    String presignedUrl = mediaServiceS3.getReadUrl(SecurityUtil.getUserKey(), groupId, mediaId.toString());
                    yield ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presignedUrl)).build();
                }
                case OSS -> {
                    String presignedUrl = mediaServiceOss.getReadUrl(SecurityUtil.getUserKey(), groupId, mediaId.toString());
                    yield ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presignedUrl)).build();
                }
                default -> throw new IllegalArgumentException("Unexpected storage value: " + fileDoc.getStorage());
            };

        } catch (IOException e) {
            log.error("Error reading media {}: {}", mediaId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // ========================= THUMBNAIL =========================

    @GetMapping("/{mediaId}/thumbnail")
    public ResponseEntity<?> getThumbnail(
            @PathVariable UUID mediaId,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false, defaultValue = "320") int maxWidth
    ) {
        try {
            UserFileDocument fileDoc = userFileService.getFile(mediaId.toString(), SecurityUtil.getUserKey(), groupId, FilePermission.READ);
            Storage storage = Storage.valueOf(fileDoc.getStorage());

            if (storage == Storage.LOCAL) {
                Path thumbPath = mediaServiceLocal.thumbnail(SecurityUtil.getUserKey(), groupId, mediaId.toString(), maxWidth);
                if (thumbPath == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(CommonResponse.from(ResponseCode.MEDIA_THUMBNAIL_NOT_AVAILABLE));
                }

                String ct = Files.probeContentType(thumbPath);
                if (ct == null) ct = MediaType.IMAGE_JPEG_VALUE;

                InputStreamResource resource = new InputStreamResource(Files.newInputStream(thumbPath));
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ct))
                        .contentLength(Files.size(thumbPath))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"thumb_" + mediaId.toString() + "\"")
                        .body(resource);

            } else if (storage == Storage.S3) {
                // For S3/OSS, redirect to the full-size URL — CDN/client handles resizing
                String presignedUrl = mediaServiceS3.getReadUrl(SecurityUtil.getUserKey(), groupId, mediaId.toString());
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presignedUrl)).build();

            } else {
                String presignedUrl = mediaServiceOss.getReadUrl(SecurityUtil.getUserKey(), groupId, mediaId.toString());
                return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presignedUrl)).build();
            }

        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (IOException e) {
            log.error("Error generating thumbnail for {}: {}", mediaId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // ========================= DELETE =========================

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<CommonResponse<?>> delete(
            @PathVariable UUID mediaId,
            @RequestParam(required = false) Set<String> deleteWithUserKeys,
            @RequestParam(required = false) UUID groupId,
            @RequestParam UUID messageId
    ) {
        try {
        	if (CollectionUtils.isEmpty(deleteWithUserKeys) && groupId == null) {
        		throw new RuntimeException(
        				"Either deleteWithUserKeys or groupId must be provided.");
        	}

            userFileService.softDeleteAndMarkForCleanupIfOrphaned(Set.of(mediaId.toString()), SecurityUtil.getUserKey(), deleteWithUserKeys, 
            		groupId, messageId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } 
    }
    
    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> batchDelete(
            @RequestBody @Valid BatchMediaDeleteRequest request
    ) {
        try {
            userFileService.softDeleteAndMarkForCleanupIfOrphaned(request.getMediaIds(), 
            		SecurityUtil.getUserKey(), request.getDeleteWithUserKeys(), request.getGroupId(), request.getMessageId());
            
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } 
    }

    // ========================= SHARE =========================
    
    @PostMapping("/{mediaId}/share")
    public ResponseEntity<?> share(
            @PathVariable UUID mediaId,
            @RequestParam(required = false) List<String> shareWithUserKeys,
            @RequestParam(required = false) UUID groupId,
            @RequestParam UUID messageId
    ) {
        try {
        	if (CollectionUtils.isEmpty(shareWithUserKeys) && groupId == null) {
        		 throw new RuntimeException(
        		            "Either shareWithUserKeys or groupId must be provided.");
        	}
        	
            userFileService.shareFile(Set.of(mediaId.toString()), SecurityUtil.getUserKey(), shareWithUserKeys, 
            		groupId, messageId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (AccessDeniedException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        }
    }
    
    @PostMapping("/share")
    public ResponseEntity<?> batchShare(
            @RequestBody @Valid BatchMediaShareRequest request
    ) {
        try {
        	if (CollectionUtils.isEmpty(request.getShareWithUserKeys()) && request.getGroupId() == null) {
        		throw new RuntimeException(
        				"Either shareWithUserKeys or groupId must be provided.");
        	}
        	
            userFileService.shareFile(request.getMediaIds(), SecurityUtil.getUserKey(), request.getShareWithUserKeys(), 
            		request.getGroupId(), request.getMessageId());
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (AccessDeniedException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        }
    }
        
    // ========================= helpers =========================

    private String resolveThumbnailUrl(UserFileDocument fileDoc, String mediaId) {
        return switch (Storage.valueOf(fileDoc.getStorage())) {
            case LOCAL -> mediaBaseUrl + "/media/" + mediaId + "/thumbnail";
            case S3    -> mediaServiceS3.getReadUrl(SecurityUtil.getUserKey(), null, mediaId);
            case OSS   -> mediaServiceOss.getReadUrl(SecurityUtil.getUserKey(), null, mediaId);
        };
    }

    private MediaUploadResponse doUpload(String userKey, MultipartFile file, String contentType,
                                          boolean encrypted, boolean autoExpire,
                                          String conversationId, UploadContext uploadContext) throws Exception {
        String active = storageProperties.getActiveUploadStorage();
        if (active == null) {
            throw new IllegalArgumentException("Active upload storage is not configured");
        }

        MediaUploadResponse resp = switch (Storage.valueOf(active.trim().toUpperCase())) {
            case LOCAL -> mediaServiceLocal.upload(userKey, file, contentType, encrypted, autoExpire, conversationId, uploadContext);
            case S3    -> mediaServiceS3.upload(userKey, file, contentType, encrypted, autoExpire, conversationId, uploadContext);
            case OSS   -> mediaServiceOss.upload(userKey, file, contentType, encrypted, autoExpire, conversationId, uploadContext);
        };
        resp.setUrl(mediaBaseUrl + resp.getUrl());
        return resp;
    }   
}
