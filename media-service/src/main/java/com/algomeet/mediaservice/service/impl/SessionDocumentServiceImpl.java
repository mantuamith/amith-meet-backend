package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.SessionDocument;
import com.algomeet.mediaservice.dto.SessionDocumentMetadataRequest;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.repository.SessionDocumentRepository;
import com.algomeet.mediaservice.service.SessionDocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionDocumentServiceImpl implements SessionDocumentService {

    private final SessionDocumentRepository repository;
    private final StorageProperties storageProperties;

    @Override
    public SessionDocument saveDocument(String sessionId, SessionDocumentMetadataRequest metadata, MultipartFile file,
            String initiatorId, String customerId) {
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name is required");
        }
        if (repository.findBySessionIdAndFileId(sessionId, metadata.getFileId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "File already exists for session");
        }
        if (metadata.getFileSize().longValue() != file.getSize()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metadata.fileSize does not match uploaded file");
        }

        Path sessionDir = resolveSessionDir(sessionId);
        String sanitizedOriginalFilename = sanitizeFilename(file.getOriginalFilename());
        Path filePath = sessionDir.resolve(metadata.getFileId() + "_" + sanitizedOriginalFilename).normalize();

        if (!filePath.startsWith(sessionDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        try {
            Files.createDirectories(sessionDir);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store document", ex);
        }

        SessionDocument document = new SessionDocument();
        document.setFileId(metadata.getFileId());
        document.setSessionId(sessionId);
        document.setConferenceFullName(metadata.getConferenceFullName());
        document.setTimestamp(metadata.getTimestamp());
        document.setFileSize(file.getSize());
        document.setFileName(sanitizedOriginalFilename);
        document.setContentType(StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream");
        document.setInitiatorId(initiatorId);
        document.setCustomerId(customerId);
        document.setAbsolutePath(filePath.toString());
        document.setStorage(Storage.LOCAL.name());
        document.setCreatedAt(Instant.now());

        return repository.save(document);
    }

    @Override
    public List<SessionDocument> listDocuments(String sessionId, int offset, int pageSize) {
        int safeOffset = Math.max(offset, 0);
        int safePageSize = Math.max(pageSize, 1);
        int page = safeOffset / safePageSize;
        return repository.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(page, safePageSize))
                .getContent();
    }

    @Override
    public SessionDocument getDocument(String sessionId, String fileId) {
        return repository.findBySessionIdAndFileId(sessionId, fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    @Override
    public Resource loadDocumentContent(String sessionId, String fileId) {
        SessionDocument document = getDocument(sessionId, fileId);
        Path path = Paths.get(document.getAbsolutePath()).normalize();

        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored document not found");
        }

        return new PathResource(path);
    }

    @Override
    public void deleteDocument(String sessionId, String fileId, String requesterId) {
        SessionDocument document = getDocument(sessionId, fileId);
        if (StringUtils.hasText(document.getInitiatorId())
                && StringUtils.hasText(requesterId)
                && !document.getInitiatorId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the initiator can delete this document");
        }

        try {
            Files.deleteIfExists(Paths.get(document.getAbsolutePath()));
        } catch (IOException ex) {
            log.warn("Failed to delete local document content fileId={} path={}", fileId, document.getAbsolutePath(), ex);
        }

        repository.delete(document);
    }

    private Path resolveSessionDir(String sessionId) {
        String baseDir = storageProperties.getLocal() != null ? storageProperties.getLocal().getDir() : null;
        if (!StringUtils.hasText(baseDir)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Local storage directory is not configured");
        }

        Path root = Paths.get(baseDir).resolve("session-documents").normalize();
        Path sessionDir = root.resolve(sessionId).normalize();
        if (!sessionDir.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sessionId");
        }
        return sessionDir;
    }

    private String sanitizeFilename(String originalFilename) {
        String candidate = Paths.get(originalFilename).getFileName().toString();
        String sanitized = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        return StringUtils.hasText(sanitized) ? sanitized : "document";
    }
}
