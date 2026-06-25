package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.SessionDocument;
import com.algomeet.mediaservice.dto.SessionDocumentMetadataRequest;
import com.algomeet.mediaservice.repository.SessionDocumentRepository;

@ExtendWith(MockitoExtension.class)
class SessionDocumentServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private SessionDocumentRepository repository;

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private StorageProperties.LocalStorage localStorage;

    @InjectMocks
    private SessionDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(storageProperties.getLocal()).thenReturn(localStorage);
        lenient().when(localStorage.getDir()).thenReturn(tempDir.toString());
    }

    @Test
    void saveDocumentStoresFileAndMetadata() throws Exception {
        SessionDocumentMetadataRequest metadata = new SessionDocumentMetadataRequest();
        metadata.setFileId("file-1");
        metadata.setConferenceFullName("room@conference.example");
        metadata.setTimestamp(1741017572040L);
        metadata.setFileSize(5L);

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());

        when(repository.findBySessionIdAndFileId("session-1", "file-1")).thenReturn(Optional.empty());
        when(repository.save(any(SessionDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SessionDocument document = service.saveDocument("session-1", metadata, file, "user-1", "42");

        assertEquals("file-1", document.getFileId());
        assertEquals("session-1", document.getSessionId());
        assertEquals("user-1", document.getInitiatorId());
        assertTrue(Files.exists(Path.of(document.getAbsolutePath())));

        ArgumentCaptor<SessionDocument> captor = ArgumentCaptor.forClass(SessionDocument.class);
        verify(repository).save(captor.capture());
        assertEquals("spec.pdf", captor.getValue().getFileName());
        assertEquals("LOCAL", captor.getValue().getStorage());
    }

    @Test
    void saveDocumentRejectsSizeMismatch() {
        SessionDocumentMetadataRequest metadata = new SessionDocumentMetadataRequest();
        metadata.setFileId("file-1");
        metadata.setConferenceFullName("room@conference.example");
        metadata.setTimestamp(1741017572040L);
        metadata.setFileSize(99L);

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());
        when(repository.findBySessionIdAndFileId("session-1", "file-1")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.saveDocument("session-1", metadata, file, "user-1", "42"));

        verify(repository, never()).save(any(SessionDocument.class));
    }

    @Test
    void listDocumentsUsesRepositoryPaging() {
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        when(repository.findBySessionIdOrderByCreatedAtDesc(eq("session-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        List<SessionDocument> result = service.listDocuments("session-1", 0, 20);

        assertEquals(1, result.size());
    }

    @Test
    void loadDocumentContentReturnsResource() throws Exception {
        Path stored = Files.writeString(tempDir.resolve("doc.txt"), "hello");
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        document.setSessionId("session-1");
        document.setAbsolutePath(stored.toString());

        when(repository.findBySessionIdAndFileId("session-1", "file-1")).thenReturn(Optional.of(document));

        Resource resource = service.loadDocumentContent("session-1", "file-1");

        assertTrue(resource.exists());
    }

    @Test
    void deleteDocumentRestrictsDeletionToInitiator() {
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        document.setSessionId("session-1");
        document.setInitiatorId("user-1");
        document.setCreatedAt(Instant.now());

        when(repository.findBySessionIdAndFileId("session-1", "file-1")).thenReturn(Optional.of(document));

        assertThrows(ResponseStatusException.class,
                () -> service.deleteDocument("session-1", "file-1", "user-2"));
    }
}
