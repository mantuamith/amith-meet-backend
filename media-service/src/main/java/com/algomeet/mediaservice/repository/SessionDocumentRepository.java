package com.algomeet.mediaservice.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.mediaservice.document.SessionDocument;

public interface SessionDocumentRepository extends JpaRepository<SessionDocument, String> {

    Optional<SessionDocument> findBySessionIdAndFileId(String sessionId, String fileId);

    Page<SessionDocument> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
}
