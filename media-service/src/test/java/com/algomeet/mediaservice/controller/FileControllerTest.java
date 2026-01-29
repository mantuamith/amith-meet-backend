package com.algomeet.mediaservice.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.algomeet.mediaservice.config.LocalizationConfig;
import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.MediaServiceS3;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.FileValidator;
import com.algomeet.mediaservice.util.MessageUtil;
import com.algomeet.mediaservice.util.SecurityUtil;

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

@WebMvcTest(controllers = FileController.class, excludeFilters = {
		@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {}) })
@ContextConfiguration(classes = FileController.class)
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
	private FileValidator fileValidator;

	private static final String USER_KEY = "user-123";

	private MockedStatic<SecurityUtil> securityUtilMock;

	@Autowired
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
		when(mediaServiceLocal.upload(any(), any(), any(), any(), anyBoolean())).thenReturn(uploadResponse);

		mockMvc.perform(multipart("/media").file(file).param("contentType", "text/plain")).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(fileValidator).validate(file, false);
		verify(mediaServiceLocal).upload(eq(USER_KEY), isNull(), eq(file), eq("text/plain"), eq(false));
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
	 * ========================= DOWNLOAD =========================
	 */

	@Test
	void download_localStorage_success() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.LOCAL.name());

		Path tempFile = Files.createTempFile("media-", ".txt");
		Files.write(tempFile, "hello".getBytes());

		when(userFileService.getFile("media1", USER_KEY, FilePermission.DOWNLOAD)).thenReturn(doc);
		when(mediaServiceLocal.download(USER_KEY, "media1")).thenReturn(tempFile);

		mockMvc.perform(get("/media/media1")).andExpect(status().isOk()).andExpect(
				header().string("Content-Disposition", "attachment; filename=\"" + tempFile.getFileName() + "\""));
	}

	@Test
	void download_s3_redirect() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.S3.name());

		when(userFileService.getFile(any(), any(), any())).thenReturn(doc);
		when(mediaServiceS3.getDownloadUrl(any(), any())).thenReturn("https://s3/presigned-url");

		mockMvc.perform(get("/media/media1")).andExpect(status().isFound())
				.andExpect(header().string("Location", "https://s3/presigned-url"));
	}

	/*
	 * ========================= DELETE =========================
	 */

	@Test
	void delete_success() throws Exception {
		mockMvc.perform(delete("/media/media1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned("media1", USER_KEY, null);
	}

	@Test
	void delete_accessDenied() throws Exception {
		doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(userFileService)
				.softDeleteAndMarkForCleanupIfOrphaned(any(), any(), any());

		mockMvc.perform(delete("/media/media1")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MEDIA_ACCESS_DENIED"));

	}

	/*
	 * ========================= SHARE =========================
	 */

	@Test
	void share_success() throws Exception {
		mockMvc.perform(post("/media/media1/share").param("shareWithUserKeys", "u1", "u2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(userFileService).shareFile("media1", USER_KEY, List.of("u1", "u2"));
	}
}
