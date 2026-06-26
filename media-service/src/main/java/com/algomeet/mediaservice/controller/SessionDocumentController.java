package com.algomeet.mediaservice.controller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.algomeet.mediaservice.controller.swagger.SessionDocumentControllerDoc;
import com.algomeet.mediaservice.document.SessionDocument;
import com.algomeet.mediaservice.dto.SessionDocumentDetailResponse;
import com.algomeet.mediaservice.dto.SessionDocumentMetadataRequest;
import com.algomeet.mediaservice.dto.SessionDocumentSummaryResponse;
import com.algomeet.mediaservice.dto.SessionDocumentUploadResponse;
import com.algomeet.mediaservice.service.SessionDocumentAccessTokenService;
import com.algomeet.mediaservice.service.SessionDocumentService;
import com.algomeet.mediaservice.util.RequestUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/v1/documents/sessions/{sessionId}/files")
@RequiredArgsConstructor
public class SessionDocumentController implements SessionDocumentControllerDoc {

    private final SessionDocumentService sessionDocumentService;
    private final SessionDocumentAccessTokenService accessTokenService;
    private final RequestUserContext requestUserContext;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @GetMapping
    @Override
    public ResponseEntity<List<SessionDocumentSummaryResponse>> listDocuments(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(name = "page-size", defaultValue = "20") Integer pageSize) {
        int safeOffset = Math.max(offset, 0);
        int safePageSize = Math.max(pageSize, 1);

        List<SessionDocumentSummaryResponse> response = sessionDocumentService.listDocuments(sessionId, safeOffset, safePageSize)
                .stream()
                .skip(safeOffset % safePageSize)
                .map(document -> SessionDocumentSummaryResponse.builder()
                        .objectId(document.getFileId())
                        .sessionId(document.getSessionId())
                        .timestamp(document.getTimestamp())
                        .contentType(document.getContentType())
                        .objectName(document.getFileName())
                        .initiatorId(document.getInitiatorId())
                        .preSignedUrl(buildDownloadUrl(document))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseEntity<SessionDocumentUploadResponse> addDocument(
            @PathVariable String sessionId,
            @RequestPart("metadata") String metadata,
            @RequestPart("file") MultipartFile file) {
        SessionDocumentMetadataRequest metadataRequest = parseAndValidateMetadata(metadata);
        SessionDocument document = sessionDocumentService.saveDocument(
                sessionId,
                metadataRequest,
                file,
                requestUserContext.getUserKey(),
                String.valueOf(requestUserContext.getTenantId()));

        return ResponseEntity.ok(new SessionDocumentUploadResponse(document.getFileId()));
    }

    @GetMapping("/{fileId}")
    @Override
    public ResponseEntity<SessionDocumentDetailResponse> getDocumentInfo(
            @PathVariable String sessionId,
            @PathVariable String fileId) {
        SessionDocument document = sessionDocumentService.getDocument(sessionId, fileId);
        return ResponseEntity.ok(SessionDocumentDetailResponse.builder()
                .fileId(document.getFileId())
                .sessionId(document.getSessionId())
                .fileName(document.getFileName())
                .customerId(document.getCustomerId())
                .userId(document.getInitiatorId())
                .presignedUrl(buildDownloadUrl(document))
                .createdAt(document.getCreatedAt() != null ? document.getCreatedAt().toEpochMilli() : null)
                .fileSize(document.getFileSize())
                .build());
    }

    @GetMapping("/{fileId}/content")
    @Override
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String sessionId,
            @PathVariable String fileId,
            @RequestParam(required = false) String token) throws IOException {
        if (!StringUtils.hasText(requestUserContext.getUserKey())) {
            accessTokenService.validateAccessToken(token, sessionId, fileId);
        }

        SessionDocument document = sessionDocumentService.getDocument(sessionId, fileId);
        Resource resource = sessionDocumentService.loadDocumentContent(sessionId, fileId);

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(document.getContentType())) {
            mediaType = MediaType.parseMediaType(document.getContentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    @Override
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String sessionId,
            @PathVariable String fileId) {
        sessionDocumentService.deleteDocument(sessionId, fileId, requestUserContext.getUserKey());
        return ResponseEntity.ok().build();
    }

    private SessionDocumentMetadataRequest parseAndValidateMetadata(String metadata) {
        SessionDocumentMetadataRequest request;
        try {
            request = objectMapper.readValue(metadata, SessionDocumentMetadataRequest.class);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metadata JSON", ex);
        }

        List<String> violations = validator.validate(request)
                .stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .sorted()
                .toList();
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid metadata: missing or invalid " + String.join(", ", violations));
        }

        return request;
    }

    private String buildDownloadUrl(SessionDocument document) {
        String token = accessTokenService.createAccessToken(document.getSessionId(), document.getFileId());
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/v1/documents/sessions/{sessionId}/files/{fileId}/content")
                .queryParam("token", token)
                .buildAndExpand(document.getSessionId(), document.getFileId())
                .toUriString();
    }
}
