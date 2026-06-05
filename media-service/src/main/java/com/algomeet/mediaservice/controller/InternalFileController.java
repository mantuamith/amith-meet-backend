package com.algomeet.mediaservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.mediaservice.controller.swagger.InternalFileControllerDoc;
import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/internal/media")
@RequiredArgsConstructor
public class InternalFileController implements InternalFileControllerDoc {

    private final UserFileService userFileService;

    @PostMapping("/{mediaId}/share")
    public ResponseEntity<?> share(
            @PathVariable String mediaId,
            @RequestParam String userKey,
            @RequestParam List<String> shareWithUserKeys,
            @RequestParam UUID messageId
    ) {
        try {
            userFileService.shareFile(List.of(mediaId), userKey, shareWithUserKeys, messageId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (AccessDeniedException e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        }
    }

    @DeleteMapping("/{mediaId}/access")
    public ResponseEntity<CommonResponse<?>> delete(
            @PathVariable String mediaId,
            @RequestParam String userKey,
            @RequestParam(required = false) List<String> deleteWithUserKeys,
            @RequestParam UUID messageId
    ) {
        try {
            userFileService.softDeleteAndMarkForCleanupIfOrphaned(List.of(mediaId), userKey, deleteWithUserKeys, messageId);
            return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
        } catch (IllegalArgumentException e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
        } catch (AccessDeniedException e) {
            log.error("Error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
        }
    }
}
