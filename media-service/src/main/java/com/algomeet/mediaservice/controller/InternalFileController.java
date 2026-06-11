package com.algomeet.mediaservice.controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.mediaservice.controller.swagger.InternalFileControllerDoc;
import com.algomeet.mediaservice.dto.BatchMediaDeleteRequest;
import com.algomeet.mediaservice.dto.BatchMediaShareRequest;
import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.SecurityUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/internal/media")
@RequiredArgsConstructor
public class InternalFileController implements InternalFileControllerDoc {

    private final UserFileService userFileService;

    @PostMapping("/{mediaId}/share")
    @Override
    public ResponseEntity<?> share(
            @PathVariable UUID mediaId,
            @RequestParam String userKey,
            @RequestParam List<String> shareWithUserKeys,
            @RequestParam UUID messageId
    ) {
        log.info("[Share] mediaId={} ownerKey={} shareWith={} messageId={}", mediaId, userKey, shareWithUserKeys, messageId);
        try {
            userFileService.shareFile(Set.of(mediaId.toString()), userKey, shareWithUserKeys, messageId);
            log.info("[Share] Success mediaId={}", mediaId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (AccessDeniedException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        }
    }
    
    @PostMapping("/share")
    @Override
    public ResponseEntity<?> batchShare(
    		@RequestParam String userKey,
            @RequestBody @Valid BatchMediaShareRequest request
    ) {
        try {
            userFileService.shareFile(request.getMediaIds(), userKey, request.getShareWithUserKeys(), request.getMessageId());
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (AccessDeniedException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        }
    }

    @DeleteMapping("/{mediaId}")
    @Override
    public ResponseEntity<CommonResponse<?>> delete(
            @PathVariable UUID mediaId,
            @RequestParam String userKey,
            @RequestParam Set<String> deleteWithUserKeys,
            @RequestParam UUID messageId
    ) {
        try {
            userFileService.softDeleteAndMarkForCleanupIfOrphaned(Set.of(mediaId.toString()), userKey, deleteWithUserKeys, messageId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        }
    } 
    
    @DeleteMapping
    @Override
    public ResponseEntity<CommonResponse<?>> batchDelete(
    		@RequestParam String userKey,
            @RequestBody @Valid BatchMediaDeleteRequest request
    ) {
        try {
            userFileService.softDeleteAndMarkForCleanupIfOrphaned(request.getMediaIds(), 
            		SecurityUtil.getUserKey(), request.getDeleteWithUserKeys(), request.getMessageId());
            
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } 
    }
}
