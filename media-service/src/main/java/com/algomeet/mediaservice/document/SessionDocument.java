package com.algomeet.mediaservice.document;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "session_document", indexes = {
        @Index(name = "idx_session_document_session_created", columnList = "session_id, created_at")
})
public class SessionDocument {

    @Id
    private String fileId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "conference_full_name", nullable = false)
    private String conferenceFullName;

    @Column(name = "uploaded_timestamp", nullable = false)
    private Long timestamp;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "initiator_id")
    private String initiatorId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "absolute_path", nullable = false)
    private String absolutePath;

    @Column(name = "storage", nullable = false)
    private String storage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
