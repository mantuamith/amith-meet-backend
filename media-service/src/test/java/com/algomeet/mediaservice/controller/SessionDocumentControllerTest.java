package com.algomeet.mediaservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.mediaservice.document.SessionDocument;
import com.algomeet.mediaservice.dto.SessionDocumentDetailResponse;
import com.algomeet.mediaservice.dto.SessionDocumentSummaryResponse;
import com.algomeet.mediaservice.dto.SessionDocumentUploadResponse;
import com.algomeet.mediaservice.service.SessionDocumentAccessTokenService;
import com.algomeet.mediaservice.service.SessionDocumentService;
import com.algomeet.mediaservice.util.RequestUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@ExtendWith(MockitoExtension.class)
class SessionDocumentControllerTest {

    @Mock
    private SessionDocumentService sessionDocumentService;

    @Mock
    private SessionDocumentAccessTokenService accessTokenService;

    @Mock
    private RequestUserContext requestUserContext;

    private SessionDocumentController controller;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        controller = new SessionDocumentController(
                sessionDocumentService,
                accessTokenService,
                requestUserContext,
                new ObjectMapper(),
                validator);
        lenient().when(requestUserContext.getUserKey()).thenReturn("user-1");
        lenient().when(requestUserContext.getTenantId()).thenReturn(42);
        lenient().when(accessTokenService.createAccessToken("session-1", "file-1")).thenReturn("signed-token");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void addDocumentReturnsFileId() throws Exception {
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");

        when(sessionDocumentService.saveDocument(eq("session-1"), any(), any(), eq("user-1"), eq("42")))
                .thenReturn(document);

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());
        MockMultipartFile metadata = new MockMultipartFile(
                "metadata",
                "",
                "application/json",
                """
                {"fileId":"file-1","conferenceFullName":"room@conference.example","timestamp":1741017572040,"fileSize":5}
                """.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<SessionDocumentUploadResponse> response = controller.addDocument("session-1",
                new String(metadata.getBytes(), StandardCharsets.UTF_8), file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("file-1", response.getBody().getFileId());
    }

    @Test
    void addDocumentRejectsMissingMetadataFields() {
        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.addDocument(
                        "session-1",
                        "{\"fileId\":\"file-1\",\"timestamp\":1741017572040,\"fileSize\":5}",
                        file));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Invalid metadata: missing or invalid conferenceFullName", exception.getReason());
    }

    @Test
    void listDocumentsReturnsMetadata() {
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        document.setSessionId("session-1");
        document.setTimestamp(1741017572040L);
        document.setContentType("application/pdf");
        document.setFileName("spec.pdf");
        document.setInitiatorId("user-1");

        when(sessionDocumentService.listDocuments("session-1", 0, 20)).thenReturn(List.of(document));

        ResponseEntity<List<SessionDocumentSummaryResponse>> response = controller.listDocuments("session-1", 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("file-1", response.getBody().get(0).getObjectId());
        assertTrue(response.getBody().get(0).getPreSignedUrl()
                .contains("/v1/documents/sessions/session-1/files/file-1/content?token=signed-token"));
    }

    @Test
    void getDocumentInfoReturnsDocumentMetadata() {
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        document.setSessionId("session-1");
        document.setFileName("spec.pdf");
        document.setCustomerId("42");
        document.setInitiatorId("user-1");
        document.setCreatedAt(Instant.ofEpochMilli(1741017572040L));
        document.setFileSize(5L);

        when(sessionDocumentService.getDocument("session-1", "file-1")).thenReturn(document);

        ResponseEntity<SessionDocumentDetailResponse> response = controller.getDocumentInfo("session-1", "file-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("file-1", response.getBody().getFileId());
        assertTrue(response.getBody().getPresignedUrl()
                .contains("/v1/documents/sessions/session-1/files/file-1/content?token=signed-token"));
    }

    @Test
    void downloadDocumentStreamsResourceForAuthenticatedRequest() throws Exception {
        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        document.setSessionId("session-1");
        document.setFileName("spec.pdf");
        document.setContentType("application/pdf");

        when(sessionDocumentService.getDocument("session-1", "file-1")).thenReturn(document);
        when(sessionDocumentService.loadDocumentContent("session-1", "file-1"))
                .thenReturn(new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller.downloadDocument("session-1", "file-1", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("inline; filename=\"spec.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void downloadDocumentValidatesSignedTokenForAnonymousRequest() throws Exception {
        when(requestUserContext.getUserKey()).thenReturn(null);

        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        document.setSessionId("session-1");
        document.setFileName("spec.pdf");
        document.setContentType("application/pdf");

        when(sessionDocumentService.getDocument("session-1", "file-1")).thenReturn(document);
        when(sessionDocumentService.loadDocumentContent("session-1", "file-1"))
                .thenReturn(new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller.downloadDocument("session-1", "file-1", "signed-token");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(accessTokenService).validateAccessToken("signed-token", "session-1", "file-1");
    }

    @Test
    void downloadDocumentRejectsProsodyTokenWhenMeetingIdMismatches() {
        when(requestUserContext.getMeetingId()).thenReturn("other-session");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.downloadDocument("session-1", "file-1", null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void deleteDocumentReturnsOk() {
        ResponseEntity<Void> response = controller.deleteDocument("session-1", "file-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(sessionDocumentService).deleteDocument("session-1", "file-1", "user-1");
    }

    @Test
    void addDocumentAllowsProsodyTokenWhenMeetingIdMatchesAndFeatureEnabled() {
        when(requestUserContext.getMeetingId()).thenReturn("session-1");
        when(requestUserContext.isFileUploadFeatureEnabled()).thenReturn(true);

        SessionDocument document = new SessionDocument();
        document.setFileId("file-1");
        when(sessionDocumentService.saveDocument(eq("session-1"), any(), any(), eq("user-1"), eq("42")))
                .thenReturn(document);

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());
        String metadata = """
                {"fileId":"file-1","conferenceFullName":"room@conference.example","timestamp":1741017572040,"fileSize":5}
                """;

        ResponseEntity<SessionDocumentUploadResponse> response = controller.addDocument("session-1", metadata, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void addDocumentRejectsProsodyTokenWhenMeetingIdMismatches() {
        when(requestUserContext.getMeetingId()).thenReturn("other-session");

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());
        String metadata = """
                {"fileId":"file-1","conferenceFullName":"room@conference.example","timestamp":1741017572040,"fileSize":5}
                """;

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.addDocument("session-1", metadata, file));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void addDocumentRejectsProsodyTokenWhenFileUploadFeatureDisabled() {
        when(requestUserContext.getMeetingId()).thenReturn("session-1");
        when(requestUserContext.isFileUploadFeatureEnabled()).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "spec.pdf", "application/pdf", "hello".getBytes());
        String metadata = """
                {"fileId":"file-1","conferenceFullName":"room@conference.example","timestamp":1741017572040,"fileSize":5}
                """;

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.addDocument("session-1", metadata, file));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void deleteDocumentRejectsProsodyTokenWhenMeetingIdMismatches() {
        when(requestUserContext.getMeetingId()).thenReturn("other-session");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.deleteDocument("session-1", "file-1"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void listDocumentsRejectsProsodyTokenWhenMeetingIdMismatches() {
        when(requestUserContext.getMeetingId()).thenReturn("other-session");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.listDocuments("session-1", 0, 20));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }
}
