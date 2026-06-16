package com.algomeet.mediaservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.algomeet.mediaservice.config.LocalizationConfig;
import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;
import com.algomeet.mediaservice.exceptions.GlobalExceptionHandler;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.MediaServiceS3;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;
import com.algomeet.mediaservice.util.FileValidator;
import com.algomeet.mediaservice.util.MessageUtil;
import com.algomeet.mediaservice.util.SecurityUtil;

@WebMvcTest(controllers = FileController.class, excludeFilters = {
		@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {}) })
@ContextConfiguration(classes = { FileController.class, GlobalExceptionHandler.class })
@Import(LocalizationConfig.class)
@EnableAutoConfiguration(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MediaServiceLocal mediaServiceLocal;

	@MockBean
	private MediaServiceS3 mediaServiceS3;

	@MockBean
	private MediaServiceOss mediaServiceOss;

	@MockBean
	private StorageProperties storageProperties;

	@MockBean
	private UserFileService userFileService;
	
	@MockBean
	private UserStorageUsageService userStorageUsageService;

	@MockBean
	private FileValidator fileValidator;
	
	@MockBean
	private UserFileRepository userFileRepository;

	private static final String USER_KEY = UUID.randomUUID().toString();
	private static final UUID MESSAGE_ID = UUID.randomUUID();
	private static final UUID MEDIA_ID1 = UUID.randomUUID();
	private static final UUID GROUP_ID = UUID.randomUUID();

	private MockedStatic<SecurityUtil> securityUtilMock;

	@MockBean
	private MessageSource messageSource;

	@BeforeEach
	void setup() {
		securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
		securityUtilMock.when(SecurityUtil::getUserKey).thenReturn(USER_KEY.toString());

		new MessageUtil(messageSource);
	}

	@AfterEach
	void tearDown() {
		if (securityUtilMock != null) {
			securityUtilMock.close();
		}
	}

	/*
	 * ========================= UPLOAD =========================
	 */

	@Test
	void upload_localStorage_success() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "test.txt", MediaType.TEXT_PLAIN_VALUE,
				"hello".getBytes());

		MediaUploadResponse uploadResponse = MediaUploadResponse.builder().build();

		when(storageProperties.getActiveUploadStorage()).thenReturn(Storage.LOCAL.name());
		when(mediaServiceLocal.upload(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any())).thenReturn(uploadResponse);

		mockMvc.perform(multipart("/media").file(file).param("contentType", "text/plain")).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(fileValidator).validate(file, false);
		verify(mediaServiceLocal).upload(eq(USER_KEY), eq(file), eq("text/plain"), eq(false), eq(true), isNull(), any());
	}

	@Test
	void upload_unsupportedMediaType() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "virus.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE,
				"x".getBytes());

		doThrow(new FileTypeNotSupportedException("not allowed")).when(fileValidator).validate(any(), anyBoolean());

		mockMvc.perform(multipart("/media").file(file)).andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("MEDIA_FILE_TYPE_NOT_SUPPORTED"));
	}

	/*
	 * ========================= READ =========================
	 */

	@Test
	void read_localStorage_success() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.LOCAL.name());

		Path tempFile = Files.createTempFile("media-", ".txt");
		Files.write(tempFile, "hello".getBytes());

		when(userFileService.getFile(MEDIA_ID1.toString())).thenReturn(doc);
		when(mediaServiceLocal.read(doc, USER_KEY, GROUP_ID)).thenReturn(tempFile);

		mockMvc.perform(get("/media/" + MEDIA_ID1).param("groupId", GROUP_ID.toString())).andExpect(status().isOk()).andExpect(
				header().string("Content-Disposition", "inline; filename=\"" + tempFile.getFileName() + "\""));
	}

	@Test
	void read_s3_redirect() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.S3.name());

		when(userFileService.getFile(any())).thenReturn(doc);
		when(mediaServiceS3.getReadUrl(eq(doc), any(), any())).thenReturn("https://s3/presigned-url");

		mockMvc.perform(get("/media/" + MEDIA_ID1).param("groupId", GROUP_ID.toString()))
		.andExpect(status().isFound())
		.andExpect(header().string("Location", "https://s3/presigned-url"));
	}

	/*
	 * ========================= DELETE =========================
	 */

	@Test
	void delete_success() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.LOCAL.name());

		when(userFileService.getFile(any(), any(), any(), any())).thenReturn(doc);
		mockMvc.perform(delete("/media/" + MEDIA_ID1).param("messageId", MESSAGE_ID.toString()).param("deleteWithUserKeys", "u1", "u2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned(any(), any(), any(), any(), any());
	}
	
	/*
	 * ========================= SHARE =========================
	 */

	@Test
	void share_success() throws Exception {
		mockMvc.perform(post("/media/" + MEDIA_ID1 + "/share").param("shareWithUserKeys", "u1", "u2")
				.param("groupId", GROUP_ID.toString())
				.param("messageId", MESSAGE_ID.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(userFileService).shareFile(Set.of(MEDIA_ID1.toString()), USER_KEY, List.of("u1", "u2"), GROUP_ID, MESSAGE_ID);
	}

	/*
	 * ========================= BATCH SHARE =========================
	 */

	@Test
	void batchShare_success() throws Exception {
	    String request = """
	        {
	          "mediaIds": ["%s"],
	          "shareWithUserKeys": ["u1", "u2"],
	          "groupId":"%s",
	          "messageId": "%s"	          
	        }
	        """.formatted(MEDIA_ID1, GROUP_ID, MESSAGE_ID);

	    mockMvc.perform(post("/media/share")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.code").value("SUCCESS"));

	    verify(userFileService).shareFile(
	    		Set.of(MEDIA_ID1.toString()),
	            USER_KEY,
	            List.of("u1", "u2"),
	            GROUP_ID,
	            MESSAGE_ID);
	}

	@Test
	void batchShare_mediaNotFound() throws Exception {
	    doThrow(new IllegalArgumentException("missing"))
	            .when(userFileService)
	            .shareFile(anySet(), anyString(), anyList(), any(), any());

	    String request = """
	        {
	          "mediaIds": ["%s"],
	          "shareWithUserKeys": ["u1"],
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, MESSAGE_ID);

	    mockMvc.perform(post("/media/share")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isNotFound())
	            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
	}

	@Test
	void batchShare_accessDenied() throws Exception {
	    doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
	            .when(userFileService)
	            .shareFile(anySet(), anyString(), anyList(), any(), any());

	    String request = """
	        {
	          "mediaIds": ["%s"],
	          "shareWithUserKeys": ["u1"],
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, MESSAGE_ID);

	    mockMvc.perform(post("/media/share")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isForbidden())
	            .andExpect(jsonPath("$.code").value("MEDIA_ACCESS_DENIED"));
	}
	
	
	
	/*
	 * ========================= BATCH DELETE =========================
	 */

	@Test
	void batchDelete_success() throws Exception {
	    String request = """
	        {
	          "mediaIds": ["%s"],
	          "deleteWithUserKeys": ["u1", "u2"],
	          "groupId":"%s",
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, GROUP_ID, MESSAGE_ID);

	    mockMvc.perform(delete("/media")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.code").value("SUCCESS"));

	    verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned(
	            Set.of(MEDIA_ID1.toString()),
	            USER_KEY,
	            Set.of("u1", "u2"),
	            GROUP_ID,
	            MESSAGE_ID);
	}

	/*
	 * ========================= BATCH UPLOAD =========================
	 */

	@Test
	void batchUpload_allSuccess_returns200() throws Exception {
		MockMultipartFile file1 = new MockMultipartFile("files", "photo1.jpg", MediaType.IMAGE_JPEG_VALUE, "img1".getBytes());
		MockMultipartFile file2 = new MockMultipartFile("files", "photo2.jpg", MediaType.IMAGE_JPEG_VALUE, "img2".getBytes());

		MediaUploadResponse resp1 = MediaUploadResponse.builder().mediaId(UUID.randomUUID().toString()).originalFilename("photo1.jpg").build();
		MediaUploadResponse resp2 = MediaUploadResponse.builder().mediaId(UUID.randomUUID().toString()).originalFilename("photo2.jpg").build();

		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.LOCAL.name());

		when(storageProperties.getActiveUploadStorage()).thenReturn(Storage.LOCAL.name());
		when(mediaServiceLocal.upload(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any()))
				.thenReturn(resp1).thenReturn(resp2);
		when(userFileService.getFile(any(), any(), any(), any())).thenReturn(doc);

		mockMvc.perform(multipart("/media/batch")
						.file(file1).file(file2)
						.param("uploadContext", "CHAT")
						.param("conversationId", "conv-123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void batchUpload_partialFailure_returns207() throws Exception {
		MockMultipartFile validFile   = new MockMultipartFile("files", "photo.jpg",  MediaType.IMAGE_JPEG_VALUE,             "img".getBytes());
		MockMultipartFile invalidFile = new MockMultipartFile("files", "virus.exe",  MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes());

		MediaUploadResponse resp = MediaUploadResponse.builder().mediaId(UUID.randomUUID().toString()).originalFilename("photo.jpg").build();

		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.LOCAL.name());

		when(storageProperties.getActiveUploadStorage()).thenReturn(Storage.LOCAL.name());

		// first call (virus.exe) throws, second call (photo.jpg) succeeds
		doThrow(new FileTypeNotSupportedException("not allowed"))
				.doNothing()
				.when(fileValidator).validate(any(), anyBoolean());

		when(mediaServiceLocal.upload(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any())).thenReturn(resp);
		when(userFileService.getFile(any(), any(), any(), any())).thenReturn(doc);

		mockMvc.perform(multipart("/media/batch")
						.file(invalidFile).file(validFile)
						.param("uploadContext", "CHAT"))
				.andExpect(status().is(207))
				.andExpect(jsonPath("$.data").isArray());
	}

	@Test
	void batchUpload_allFilesRejected_returns415() throws Exception {
		MockMultipartFile file1 = new MockMultipartFile("files", "a.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes());
		MockMultipartFile file2 = new MockMultipartFile("files", "b.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, "y".getBytes());

		doThrow(new FileTypeNotSupportedException("not allowed"))
				.when(fileValidator).validate(any(), anyBoolean());

		mockMvc.perform(multipart("/media/batch").file(file1).file(file2))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("MEDIA_FILE_TYPE_NOT_SUPPORTED"));
	}

	@Test
	void upload_fileTooLarge_returns413() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "big.jpg", MediaType.IMAGE_JPEG_VALUE, "data".getBytes());

		doThrow(new MaxUploadSizeExceededException(50 * 1024 * 1024L))
				.when(fileValidator).validate(any(), anyBoolean());

		mockMvc.perform(multipart("/media").file(file))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error").value("Payload Too Large"))
				.andExpect(jsonPath("$.message").value("File size exceeds the maximum allowed limit"));

		verify(mediaServiceLocal, never()).upload(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
	}

	@Test
	void batchUpload_fileTooLarge_returns413() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "big.jpg", MediaType.IMAGE_JPEG_VALUE, "data".getBytes());

		doThrow(new MaxUploadSizeExceededException(50 * 1024 * 1024L))
				.when(fileValidator).validate(any(), anyBoolean());

		mockMvc.perform(multipart("/media/batch").file(file))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error").value("Payload Too Large"))
				.andExpect(jsonPath("$.message").value("File size exceeds the maximum allowed limit"));

		verify(mediaServiceLocal, never()).upload(any(), any(), any(), anyBoolean(), anyBoolean(), any(), any());
	}

	@Test
	void batchDelete_mediaNotFound() throws Exception {
	    doThrow(new IllegalArgumentException("missing"))
	            .when(userFileService)
	            .softDeleteAndMarkForCleanupIfOrphaned(anySet(), anyString(), anySet(), any(), any());

	    String request = """
	        {
	          "mediaIds": ["%s"],
	          "deleteWithUserKeys": ["u1"],
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, MESSAGE_ID);

	    mockMvc.perform(delete("/media")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isNotFound())
	            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
	}	
}
