package com.algomeet.mediaservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;

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

	@BeforeEach
	void setUp() {
		controller = new InternalFileController(userFileService);
		new MessageUtil(messageSource);
	}

	@Test
	void share_returnsOkOnSuccess() {
		ResponseEntity<?> response = controller.share("media-1", "user-1", List.of("user-2", "user-3"));

		verify(userFileService).shareFile("media-1", "user-1", List.of("user-2", "user-3"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(((CommonResponse<?>) response.getBody()).getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
	}

	@Test
	void share_returnsNotFoundWhenMediaMissing() {
		doThrow(new IllegalArgumentException("missing")).when(userFileService)
				.shareFile("media-1", "user-1", List.of("user-2"));

		ResponseEntity<?> response = controller.share("media-1", "user-1", List.of("user-2"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(((CommonResponse<?>) response.getBody()).getCode()).isEqualTo(ResponseCode.MEDIA_NOT_FOUND.getCode());
	}

	@Test
	void share_returnsForbiddenWhenAccessDenied() {
		doThrow(new AccessDeniedException("denied")).when(userFileService)
				.shareFile("media-1", "user-1", List.of("user-2"));

		ResponseEntity<?> response = controller.share("media-1", "user-1", List.of("user-2"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(((CommonResponse<?>) response.getBody()).getCode()).isEqualTo(ResponseCode.MEDIA_ACCESS_DENIED.getCode());
	}

	@Test
	void delete_returnsOkOnSuccess() {
		ResponseEntity<CommonResponse<?>> response = controller.delete("media-1", "user-1", List.of("user-2"));

		verify(userFileService).softDeleteAndMarkForCleanupIfOrphaned("media-1", "user-1", List.of("user-2"));
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getCode()).isEqualTo(ResponseCode.SUCCESS.getCode());
	}

	@Test
	void delete_returnsNotFoundWhenMediaMissing() {
		doThrow(new IllegalArgumentException("missing")).when(userFileService)
				.softDeleteAndMarkForCleanupIfOrphaned("media-1", "user-1", List.of("user-2"));

		ResponseEntity<CommonResponse<?>> response = controller.delete("media-1", "user-1", List.of("user-2"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().getCode()).isEqualTo(ResponseCode.MEDIA_NOT_FOUND.getCode());
	}

	@Test
	void delete_returnsForbiddenWhenAccessDenied() {
		doThrow(new AccessDeniedException("denied")).when(userFileService)
				.softDeleteAndMarkForCleanupIfOrphaned("media-1", "user-1", List.of("user-2"));

		ResponseEntity<CommonResponse<?>> response = controller.delete("media-1", "user-1", List.of("user-2"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody().getCode()).isEqualTo(ResponseCode.MEDIA_ACCESS_DENIED.getCode());
	}
}
