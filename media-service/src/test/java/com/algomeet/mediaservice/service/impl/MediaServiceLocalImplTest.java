package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
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
    
	@Mock
	private UserStorageUsageService userStorageUsageService;

	@Mock
	private com.algomeet.mediaservice.util.MediaMetadataExtractor metadataExtractor;

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

        // when
        MediaUploadResponse response = mediaService.upload(
        		OWNER_KEY,
                file,
                null,
                false,
                true,
                null,
                null
        );

        // then
        assertNotNull(response.getMediaId());
        assertEquals("test.txt", response.getOriginalFilename());
        assertEquals("text/plain", response.getContentType());
        assertEquals(file.getSize(), response.getSize());
        assertFalse(response.isEncrypted());
        assertTrue(response.getUrl().contains(response.getMediaId()));

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
        assertEquals(true, saved.getCleanupEligibleAt() != null);
    }

    @Test
    void read_shouldReturnFilePath_whenAuthorized() throws Exception {
        // given
        Path filePath = Files.createTempFile(tempDir, "media-", ".bin");

        UserFileDocument doc = new UserFileDocument();
        doc.setAbsolutePath(filePath.toString());
        doc.setFilename("media.bin");

        when(userFileService.getFile(
                eq("media-id"),
                eq("11111111-1111-1111-1111-111111111111"),
                eq(FilePermission.READ))
        ).thenReturn(doc);

        // when
        Path result = mediaService.read("11111111-1111-1111-1111-111111111111", "media-id");

        // then
        assertEquals(filePath, result);
        assertTrue(Files.exists(result));
    }

    @Test
    void read_shouldThrowException_whenFileMissing() {
        // given
        UserFileDocument doc = new UserFileDocument();
        doc.setAbsolutePath(tempDir.resolve("missing.file").toString());
        doc.setFilename("missing.file");

        when(userFileService.getFile(any(), any(), any()))
                .thenReturn(doc);

        // then
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> mediaService.read("11111111-1111-1111-1111-111111111111", "media-id")
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
