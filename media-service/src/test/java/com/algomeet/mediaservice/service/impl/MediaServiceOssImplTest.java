package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.aliyun.oss.OSS;

@ExtendWith(MockitoExtension.class)
class MediaServiceOssImplTest {

    @Mock
    private OSS ossClient;

    @Mock
    private UserFileService userFileService;

    @Mock
    private StorageProperties storageProperties;

    @Mock
    private StorageProperties.Oss ossStorage;

    @InjectMocks
    private MediaServiceOssImpl mediaService;

    @BeforeEach
    void setup() {
    }

    /* =========================
       UPLOAD
       ========================= */

    @Test
    void upload_shouldUploadToOssAndPersistMetadata() throws Exception {
        // given
        StorageProperties.Oss ossStorage = mock(StorageProperties.Oss.class);
        when(storageProperties.getOss()).thenReturn(ossStorage);
        when(ossStorage.getBucket()).thenReturn("test-bucket");
        
        // given
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello oss".getBytes()
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

        // verify OSS upload
        verify(ossClient).putObject(
                eq("test-bucket"),
                contains("_test.txt"),
                any(InputStream.class)
        );

        // verify DB persistence
        ArgumentCaptor<UserFileDocument> captor =
                ArgumentCaptor.forClass(UserFileDocument.class);
        verify(userFileService).create(captor.capture());

        UserFileDocument saved = captor.getValue();
        assertEquals(OWNER_KEY, saved.getOwner());
        assertEquals(file.getSize(), saved.getSize());
        assertEquals("OSS", saved.getStorage());
        assertFalse(saved.isEncrypted());

        // owner + shared users
        assertEquals(3, saved.getAccessControlList().size());
    }

    /* =========================
       DOWNLOAD (SIGNED URL)
       ========================= */

    @Test
    void getDownloadUrl_shouldReturnSignedUrl() throws Exception {
        when(storageProperties.getOss()).thenReturn(ossStorage);
        when(ossStorage.getBucket()).thenReturn("test-bucket");
        when(ossStorage.getDownloadMaxDurationInMinutes()).thenReturn(15);
        
        // given
        UserFileDocument doc = new UserFileDocument();
        doc.setAbsolutePath("media/key.txt");

        when(userFileService.getFile(
                eq("media-id"),
                eq("11111111-1111-1111-1111-111111111111"),
                eq(FilePermission.DOWNLOAD))
        ).thenReturn(doc);

        URL signedUrl = new URL("https://oss-signed-url");

        when(ossClient.generatePresignedUrl(
                eq("test-bucket"),
                eq("media/key.txt"),
                any()
        )).thenReturn(signedUrl);

        // when
        String url = mediaService.getDownloadUrl("11111111-1111-1111-1111-111111111111", "media-id");

        // then
        assertEquals("https://oss-signed-url", url);
    }

    /* =========================
       DELETE
       ========================= */

    @Test
    void deleteIfExists_shouldDeleteObject_whenExists() {
        // given
        StorageProperties.Oss ossStorage = mock(StorageProperties.Oss.class);
        when(storageProperties.getOss()).thenReturn(ossStorage);
        when(ossStorage.getBucket()).thenReturn("test-bucket");

        when(ossClient.doesObjectExist("test-bucket", "media/key.txt"))
                .thenReturn(true);

        // when
        boolean deleted = mediaService.deleteIfExists("media/key.txt");

        // then
        assertTrue(deleted);
        verify(ossClient).deleteObject("test-bucket", "media/key.txt");
    }

    @Test
    void deleteIfExists_shouldReturnFalse_whenObjectMissing() {
        when(storageProperties.getOss()).thenReturn(ossStorage);
        when(ossStorage.getBucket()).thenReturn("test-bucket");
        
        // given
        when(ossClient.doesObjectExist("test-bucket", "missing.txt"))
                .thenReturn(false);

        // when
        boolean deleted = mediaService.deleteIfExists("missing.txt");

        // then
        assertFalse(deleted);
        verify(ossClient, never()).deleteObject(any(), any());
    }

    @Test
    void deleteIfExists_shouldReturnFalse_whenKeyIsBlank() {
        assertFalse(mediaService.deleteIfExists(null));
        assertFalse(mediaService.deleteIfExists(""));
        assertFalse(mediaService.deleteIfExists("   "));
    }
}
