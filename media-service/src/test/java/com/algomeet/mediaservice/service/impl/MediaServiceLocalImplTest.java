package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.service.UserFileService;

@ExtendWith(MockitoExtension.class)
class MediaServiceLocalImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private StorageProperties.LocalStorage localStorage;

    @Mock
    private UserFileService userFileService;

    @InjectMocks
    private MediaServiceLocalImpl mediaService;

    @BeforeEach
    void setup() {
    }

    @Test
    void upload_shouldStoreFileAndPersistMetadata() throws Exception {
        when(storageProperties.getLocal()).thenReturn(localStorage);
        when(localStorage.getDir()).thenReturn(tempDir.toString());
        
        // given
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello world".getBytes()
        );

        final String OWNER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111").toString();
        final List<String> SHARED_WITH = List.of(
                UUID.fromString("22222222-2222-2222-2222-222222222222").toString(),
                UUID.fromString("33333333-3333-3333-3333-333333333333").toString());

        // when
        MediaUploadResponse response = mediaService.upload(
        		OWNER_KEY,
        		SHARED_WITH,
                file,
                null,
                false
        );

        // then
        assertNotNull(response.getMediaId());
        assertEquals("test.txt", response.getOriginalFilename());
        assertEquals("text/plain", response.getContentType());
        assertEquals(file.getSize(), response.getSize());
        assertFalse(response.isEncrypted());
        assertTrue(response.getDownloadUrl().contains(response.getMediaId()));

        // verify file persisted on disk
        Path storedFile = Files.list(tempDir).findFirst().orElseThrow();
        assertTrue(Files.exists(storedFile));

        // verify DB persistence
        ArgumentCaptor<UserFileDocument> captor =
                ArgumentCaptor.forClass(UserFileDocument.class);
        verify(userFileService).create(captor.capture());

        UserFileDocument saved = captor.getValue();
        assertEquals(OWNER_KEY, saved.getOwner());
        assertEquals(response.getMediaId(), saved.getId());
        assertEquals(file.getSize(), saved.getSize());
        assertFalse(saved.isEncrypted());
        assertEquals("LOCAL", saved.getStorage());
        assertEquals(2, saved.getAccessControlList().size());
    }

    @Test
    void download_shouldReturnFilePath_whenAuthorized() throws Exception {
        // given
        Path filePath = Files.createTempFile(tempDir, "media-", ".bin");

        UserFileDocument doc = new UserFileDocument();
        doc.setAbsolutePath(filePath.toString());
        doc.setFilename("media.bin");

        when(userFileService.getFile(
                eq("media-id"),
                eq("11111111-1111-1111-1111-111111111111"),
                eq(FilePermission.DOWNLOAD))
        ).thenReturn(doc);

        // when
        Path result = mediaService.download("11111111-1111-1111-1111-111111111111", "media-id");

        // then
        assertEquals(filePath, result);
        assertTrue(Files.exists(result));
    }

    @Test
    void download_shouldThrowException_whenFileMissing() {
        // given
        UserFileDocument doc = new UserFileDocument();
        doc.setAbsolutePath(tempDir.resolve("missing.file").toString());
        doc.setFilename("missing.file");

        when(userFileService.getFile(any(), any(), any()))
                .thenReturn(doc);

        // then
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> mediaService.download("11111111-1111-1111-1111-111111111111", "media-id")
        );

        assertTrue(ex.getMessage().contains("File not found"));
    }

    @Test
    void deleteIfExists_shouldDeleteFile() throws IOException {
        // given
        Path file = Files.createTempFile(tempDir, "delete-", ".txt");
        assertTrue(Files.exists(file));

        // when
        boolean deleted = mediaService.deleteIfExists(file.toString());

        // then
        assertTrue(deleted);
        assertFalse(Files.exists(file));
    }
}
