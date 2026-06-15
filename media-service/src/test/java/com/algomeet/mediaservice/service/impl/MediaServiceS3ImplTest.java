package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.service.UserFileService;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class MediaServiceS3ImplTest {

	@Mock
	private S3Client s3Client;

	@Mock
	private UserFileService userFileService;

	@Mock
	private StorageProperties storageProperties;

	@Mock
	private StorageProperties.S3Storage s3Storage;

	@InjectMocks
	private MediaServiceS3Impl mediaService;
	
	@Mock
	private UserStorageUsageService userStorageUsageService;

	@Mock
	private com.algomeet.mediaservice.util.MediaMetadataExtractor metadataExtractor;

	@BeforeEach
	void setup() {
	}

	@Test
	void upload_shouldUploadToS3AndPersistMetadata() throws Exception {
		when(storageProperties.getS3()).thenReturn(s3Storage);
		when(s3Storage.getBucket()).thenReturn("test-bucket");
	        
		// given
		MultipartFile file = new MockMultipartFile("file", "hello.txt", "text/plain", "hello s3".getBytes());

		// when
		MediaUploadResponse response = mediaService.upload("11111111-1111-1111-1111-111111111111", file, null, false, true, null, null);

		// then
		assertNotNull(response.getMediaId());
		assertEquals("hello.txt", response.getOriginalFilename());
		assertEquals("text/plain", response.getContentType());
		assertEquals(file.getSize(), response.getSize());
		assertFalse(response.isEncrypted());

		verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

		ArgumentCaptor<UserFileDocument> captor = ArgumentCaptor.forClass(UserFileDocument.class);
		verify(userFileService).create(captor.capture());

		UserFileDocument saved = captor.getValue();
		assertEquals("11111111-1111-1111-1111-111111111111", saved.getOwner());
		assertEquals("S3", saved.getStorage());
		assertEquals(file.getSize(), saved.getSize());
		assertFalse(saved.isEncrypted());
        assertEquals(true, saved.getCleanupEligibleAt() != null);
	}

	@Test
	void getReadUrl_shouldReturnPresignedUrl() throws Exception {
		when(storageProperties.getS3()).thenReturn(s3Storage);
		when(s3Storage.getBucket()).thenReturn("test-bucket");
		
	    // storage config
	    when(s3Storage.getRegion()).thenReturn("ap-southeast-1");
	    when(s3Storage.getSigExpirationInMinutes()).thenReturn(15);
	    when(s3Storage.getBucket()).thenReturn("test-bucket");

	    // file permission check
	    UserFileDocument doc = new UserFileDocument();
	    doc.setAbsolutePath("media/key.txt");

	    when(userFileService.hasPermission(
	            eq(doc),
	            eq("11111111-1111-1111-1111-111111111111"),
	            eq(UUID.fromString("22211111-1111-1111-1111-111111111111")),
	            eq(FilePermission.READ)))
	        .thenReturn(true);

	    // presigned URL
	    PresignedGetObjectRequest presigned =
	            mock(PresignedGetObjectRequest.class);
	    when(presigned.url()).thenReturn(new URL("https://signed-url"));

	    // presigner
	    S3Presigner presigner = mock(S3Presigner.class);
	    when(presigner.presignGetObject(any(GetObjectPresignRequest.class)))
	            .thenReturn(presigned);

	    // 🔑 mock the BUILDER (this is the missing piece)
	    S3Presigner.Builder builder = mock(S3Presigner.Builder.class);
	    when(builder.region(any(Region.class))).thenReturn(builder);
	    when(builder.build()).thenReturn(presigner);

	    try (MockedStatic<S3Presigner> mocked =
	                 mockStatic(S3Presigner.class)) {

	        mocked.when(S3Presigner::builder).thenReturn(builder);

	        // when
	        String url = mediaService.getReadUrl(doc, "11111111-1111-1111-1111-111111111111", UUID.fromString("22211111-1111-1111-1111-111111111111"));

	        // then
	        assertEquals("https://signed-url", url);
	        verify(presigner).close();
	    }
	}

	@Test
	void getDownloadUrl_shouldFail_whenMediaIdMissing() {
		RuntimeException ex = assertThrows(RuntimeException.class, () -> mediaService.getReadUrl("user-1", UUID.fromString("22211111-1111-1111-1111-111111111111"), ""));
		assertTrue(ex.getMessage().contains("Media Id"));
	}

	@Test
	void deleteIfExists_shouldDeleteObject() {
		when(storageProperties.getS3()).thenReturn(s3Storage);
		when(s3Storage.getBucket()).thenReturn("test-bucket");
		// when
		boolean deleted = mediaService.deleteIfExists("object-key");

		// then
		assertTrue(deleted);
		verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
	}

	@Test
	void deleteIfExists_shouldReturnFalse_whenNotFound() {
		when(storageProperties.getS3()).thenReturn(s3Storage);
		when(s3Storage.getBucket()).thenReturn("test-bucket");
		
		// given
		S3Exception notFound = (S3Exception) S3Exception.builder().statusCode(404).message("Not found").build();

		doThrow(notFound).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

		// when
		boolean deleted = mediaService.deleteIfExists("missing-key");

		// then
		assertFalse(deleted);
	}
}
