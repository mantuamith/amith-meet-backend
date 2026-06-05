package com.algomeet.mediaservice.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.algomeet.mediaservice.config.LocalizationConfig;
import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.MediaServiceS3;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.service.impl.UserStorageUsageService;
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
	private UserStorageUsageService userStorageUsageService;

	@MockBean
	private FileValidator fileValidator;
	
	@MockBean
	private UserFileRepository userFileRepository;

	private static final String USER_KEY = UUID.randomUUID().toString();
	private static final UUID MESSAGE_ID = UUID.randomUUID();
	private static final UUID MEDIA_ID1 = UUID.randomUUID();

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

		when(userFileService.getFile(MEDIA_ID1.toString() , USER_KEY, FilePermission.READ)).thenReturn(doc);
		when(mediaServiceLocal.read(USER_KEY, MEDIA_ID1.toString())).thenReturn(tempFile);

		mockMvc.perform(get("/media/" + MEDIA_ID1)).andExpect(status().isOk()).andExpect(
				header().string("Content-Disposition", "inline; filename=\"" + tempFile.getFileName() + "\""));
	}

	@Test
	void read_s3_redirect() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.S3.name());

		when(userFileService.getFile(any(), any(), any())).thenReturn(doc);
		when(mediaServiceS3.getReadUrl(any(), any())).thenReturn("https://s3/presigned-url");

		mockMvc.perform(get("/media/" + MEDIA_ID1 )).andExpect(status().isFound())
				.andExpect(header().string("Location", "https://s3/presigned-url"));
	}

	/*
	 * ========================= DELETE =========================
	 */

	@Test
	void delete_success() throws Exception {
		UserFileDocument doc = new UserFileDocument();
		doc.setStorage(Storage.LOCAL.name());

		when(userFileService.getFile(any(), any(), any())).thenReturn(doc);
		mockMvc.perform(delete("/media/" + MEDIA_ID1 + "/access").param("messageId", MESSAGE_ID.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned(any(), any(), any(), any());
	}
	
	/*
	 * ========================= SHARE =========================
	 */

	@Test
	void share_success() throws Exception {
		mockMvc.perform(post("/media/" + MEDIA_ID1 + "/share").param("shareWithUserKeys", "u1", "u2")
				.param("messageId", MESSAGE_ID.toString())).andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS"));

		verify(userFileService).shareFile(List.of(MEDIA_ID1.toString()), USER_KEY, List.of("u1", "u2"), MESSAGE_ID);
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
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, MESSAGE_ID);

	    mockMvc.perform(post("/media/share")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.code").value("SUCCESS"));

	    verify(userFileService).shareFile(
	            List.of(MEDIA_ID1.toString()),
	            USER_KEY,
	            List.of("u1", "u2"),
	            MESSAGE_ID);
	}

	@Test
	void batchShare_mediaNotFound() throws Exception {
	    doThrow(new IllegalArgumentException("missing"))
	            .when(userFileService)
	            .shareFile(anyList(), anyString(), anyList(), any());

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
	            .shareFile(anyList(), anyString(), anyList(), any());

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
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, MESSAGE_ID);

	    mockMvc.perform(delete("/media/access")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isOk())
	            .andExpect(jsonPath("$.code").value("SUCCESS"));

	    verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned(
	            List.of(MEDIA_ID1.toString()),
	            USER_KEY,
	            List.of("u1", "u2"),
	            MESSAGE_ID);
	}

	@Test
	void batchDelete_mediaNotFound() throws Exception {
	    doThrow(new IllegalArgumentException("missing"))
	            .when(userFileService)
	            .softDeleteAndMarkForCleanupIfOrphaned(anyList(), anyString(), anyList(), any());

	    String request = """
	        {
	          "mediaIds": ["%s"],
	          "deleteWithUserKeys": ["u1"],
	          "messageId": "%s"
	        }
	        """.formatted(MEDIA_ID1, MESSAGE_ID);

	    mockMvc.perform(delete("/media/access")
	            .contentType(MediaType.APPLICATION_JSON)
	            .content(request))
	            .andExpect(status().isNotFound())
	            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
	}	
}
