package com.algomeet.mediaservice.service;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.document.SessionDocument;
import com.algomeet.mediaservice.dto.SessionDocumentMetadataRequest;

public interface SessionDocumentService {

    SessionDocument saveDocument(String sessionId, SessionDocumentMetadataRequest metadata, MultipartFile file,
            String initiatorId, String customerId);

    List<SessionDocument> listDocuments(String sessionId, int offset, int pageSize);

    SessionDocument getDocument(String sessionId, String fileId);

    Resource loadDocumentContent(String sessionId, String fileId);

    void deleteDocument(String sessionId, String fileId, String requesterId);
}
