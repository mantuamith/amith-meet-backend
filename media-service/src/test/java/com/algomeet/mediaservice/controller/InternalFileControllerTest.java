package com.algomeet.mediaservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import com.algomeet.mediaservice.config.LocalizationConfig;
import com.algomeet.mediaservice.dto.BatchMediaDeleteRequest;
import com.algomeet.mediaservice.dto.BatchMediaShareRequest;
import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.MessageUtil;

@ExtendWith(MockitoExtension.class)
@Import(LocalizationConfig.class)
class InternalFileControllerTest {
	@Mock
	private UserFileService userFileService;

	private InternalFileController controller;
	
	@Mock
	private MessageSource messageSource;
	
	private static UUID MESSAGE_ID = UUID.randomUUID();
	private static final UUID FILE_ID = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		controller = new InternalFileController(userFileService);
		new MessageUtil(messageSource);
	}

	@Test
	void share_returnsOkOnSuccess() {
		ResponseEntity<?> response = controller.share(FILE_ID, "user-1", List.of("user-2", "user-3"), MESSAGE_ID);

		verify(userFileService).shareFile(List.of(FILE_ID.toString()), "user-1", List.of("user-2", "user-3"), MESSAGE_ID);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(((CommonResponse<?>) response.getBody()).getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
	}

	@Test
	void share_returnsNotFoundWhenMediaMissing() {
		doThrow(new IllegalArgumentException("missing")).when(userFileService)
				.shareFile(List.of(FILE_ID.toString()), "user-1", List.of("user-2"), MESSAGE_ID);

		ResponseEntity<?> response = controller.share(FILE_ID, "user-1", List.of("user-2"), MESSAGE_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(((CommonResponse<?>) response.getBody()).getCode()).isEqualTo(ResponseCode.MEDIA_NOT_FOUND.getCode());
	}

	@Test
	void share_returnsForbiddenWhenAccessDenied() {
		doThrow(new AccessDeniedException("denied")).when(userFileService)
				.shareFile(List.of(FILE_ID.toString()), "user-1", List.of("user-2"), MESSAGE_ID);

		ResponseEntity<?> response = controller.share(FILE_ID, "user-1", List.of("user-2"), MESSAGE_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(((CommonResponse<?>) response.getBody()).getCode()).isEqualTo(ResponseCode.MEDIA_ACCESS_DENIED.getCode());
	}
	
	@Test
	void batchShare_returnsOkOnSuccess() {
	    BatchMediaShareRequest request = new BatchMediaShareRequest();
	    request.setMediaIds(List.of(FILE_ID.toString()));
	    request.setShareWithUserKeys(List.of("user-2", "user-3"));
	    request.setMessageId(MESSAGE_ID);

	    ResponseEntity<?> response = controller.batchShare("user-1", request);

	    verify(userFileService).shareFile(
	            request.getMediaIds(),
	            "user-1",
	            request.getShareWithUserKeys(),
	            MESSAGE_ID);

	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	    assertThat(((CommonResponse<?>) response.getBody()).getCode())
	            .isEqualTo(ResponseCode.SUCCESS.getCode());
	}

	@Test
	void batchShare_returnsNotFoundWhenMediaMissing() {
	    BatchMediaShareRequest request = new BatchMediaShareRequest();
	    request.setMediaIds(List.of(FILE_ID.toString()));
	    request.setShareWithUserKeys(List.of("user-2"));
	    request.setMessageId(MESSAGE_ID);

	    doThrow(new IllegalArgumentException("missing"))
	            .when(userFileService)
	            .shareFile(
	                    request.getMediaIds(),
	                    "user-1",
	                    request.getShareWithUserKeys(),
	                    MESSAGE_ID);

	    ResponseEntity<?> response = controller.batchShare("user-1", request);

	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	    assertThat(((CommonResponse<?>) response.getBody()).getCode())
	            .isEqualTo(ResponseCode.MEDIA_NOT_FOUND.getCode());
	}

	@Test
	void batchShare_returnsForbiddenWhenAccessDenied() {
	    BatchMediaShareRequest request = new BatchMediaShareRequest();
	    request.setMediaIds(List.of(FILE_ID.toString()));
	    request.setShareWithUserKeys(List.of("user-2"));
	    request.setMessageId(MESSAGE_ID);

	    doThrow(new AccessDeniedException("denied"))
	            .when(userFileService)
	            .shareFile(
	                    request.getMediaIds(),
	                    "user-1",
	                    request.getShareWithUserKeys(),
	                    MESSAGE_ID);

	    ResponseEntity<?> response = controller.batchShare("user-1", request);

	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	    assertThat(((CommonResponse<?>) response.getBody()).getCode())
	            .isEqualTo(ResponseCode.MEDIA_ACCESS_DENIED.getCode());
	}

	@Test
	void delete_returnsOkOnSuccess() {
		ResponseEntity<CommonResponse<?>> response = controller.delete(FILE_ID, "user-1", List.of("user-2"), MESSAGE_ID);

		verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned(List.of(FILE_ID.toString()), "user-1", List.of("user-2"), MESSAGE_ID);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
	}

	@Test
	void delete_returnsNotFoundWhenMediaMissing() {
		doThrow(new IllegalArgumentException("missing")).when(userFileService)
				.softDeleteAndMarkForCleanupIfOrphaned(List.of(FILE_ID.toString()), "user-1", List.of("user-2"), MESSAGE_ID);

		ResponseEntity<CommonResponse<?>> response = controller.delete(FILE_ID, "user-1", List.of("user-2"), MESSAGE_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().getCode()).isEqualTo(ResponseCode.MEDIA_NOT_FOUND.getCode());
	}

	@Test
	void delete_shouldReturnSuccess_whenUnauthorizedUsersAreSkipped() {
	    ResponseEntity<CommonResponse<?>> response =
	            controller.delete(FILE_ID, "user-1", List.of("user-2"), MESSAGE_ID);

	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	    assertThat(response.getBody().getCode())
	            .isEqualTo(ResponseCode.SUCCESS.getCode());
	}
	
	@Test
	void batchDelete_returnsOkOnSuccess() {
	    BatchMediaDeleteRequest request = new BatchMediaDeleteRequest();
	    request.setMediaIds(List.of(FILE_ID.toString()));
	    request.setDeleteWithUserKeys(List.of("user-2"));
	    request.setMessageId(MESSAGE_ID);

	    ResponseEntity<CommonResponse<?>> response =
	            controller.batchDelete("user-1", request);

	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	    assertThat(response.getBody().getCode())
	            .isEqualTo(ResponseCode.SUCCESS.getCode());
	}

	@Test
	void batchDelete_returnsNotFoundWhenMediaMissing() {
	    BatchMediaDeleteRequest request = new BatchMediaDeleteRequest();
	    request.setMediaIds(List.of(FILE_ID.toString()));
	    request.setDeleteWithUserKeys(List.of("user-2"));
	    request.setMessageId(MESSAGE_ID);

	    doThrow(new IllegalArgumentException("missing"))
	            .when(userFileService)
	            .softDeleteAndMarkForCleanupIfOrphaned(
	                    request.getMediaIds(),
	                    null,
	                    request.getDeleteWithUserKeys(),
	                    MESSAGE_ID);

	    ResponseEntity<CommonResponse<?>> response =
	            controller.batchDelete("user-1", request);

	    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	    assertThat(response.getBody().getCode())
	            .isEqualTo(ResponseCode.MEDIA_NOT_FOUND.getCode());
	}	
	
}
