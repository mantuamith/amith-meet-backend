package com.algomeet.mediaservice.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.controller.swagger.FileControllerDoc;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.CommonResponse;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.enums.ResponseCode;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.MediaServiceS3;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.FileValidator;
import com.algomeet.mediaservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class FileController implements FileControllerDoc {
	private final MediaServiceLocal mediaServiceLocal;
	private final MediaServiceS3 mediaServiceS3;
	private final MediaServiceOss mediaServiceOss;
	private final StorageProperties storageProperties;
	private final UserFileService userFileService;
	private final FileValidator fileValidator;

	/**
	 * Upload media file
	 * 
	 * @throws IOException
	 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<CommonResponse<MediaUploadResponse>> upload(@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) List<String> sharedWithUserKeys,
			@RequestParam(required = false) String contentType, @RequestParam(required = false) Boolean encrypted)
			throws Exception {

		log.info("Uploading media: name={}, size={} bytes", file.getOriginalFilename(), file.getSize());

		try {
			// Validate file
			fileValidator.validate(file, encrypted != null && encrypted);
		} catch (FileTypeNotSupportedException ex) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
					.body(CommonResponse.from(ResponseCode.MEDIA_FILE_TYPE_NOT_SUPPORTED));
		}

		MediaUploadResponse response = null;
		if (storageProperties.getActiveUploadStorage() != null
				&& Storage.LOCAL.name().equalsIgnoreCase(storageProperties.getActiveUploadStorage().trim())) {

			response = mediaServiceLocal.upload(SecurityUtil.getUserKey(), sharedWithUserKeys, file, contentType,
					encrypted != null && encrypted);

		} else if (storageProperties.getActiveUploadStorage() != null
				&& Storage.S3.name().equalsIgnoreCase(storageProperties.getActiveUploadStorage().trim())) {

			response = mediaServiceS3.upload(SecurityUtil.getUserKey(), sharedWithUserKeys, file, contentType,
					encrypted != null && encrypted);
		} else if (storageProperties.getActiveUploadStorage() != null
				&& Storage.OSS.name().equalsIgnoreCase(storageProperties.getActiveUploadStorage().trim())) {

			response = mediaServiceOss.upload(SecurityUtil.getUserKey(), sharedWithUserKeys, file, contentType,
					encrypted != null && encrypted);
		} else {
			throw new IllegalArgumentException("Unexpected configuration active upload storage value: "
					+ Storage.valueOf(storageProperties.getActiveUploadStorage()));
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, response));
	}

	@GetMapping("/{mediaId}")
	public ResponseEntity<?> getMedia(@PathVariable String mediaId) {
		try {
			UserFileDocument fileDoc = userFileService.getFile(mediaId, SecurityUtil.getUserKey(), FilePermission.READ);

			return switch (Storage.valueOf(fileDoc.getStorage())) {
			case LOCAL -> {
				Path filePath = mediaServiceLocal.read(SecurityUtil.getUserKey(), mediaId);

				String contentType = Files.probeContentType(filePath);
				if (contentType == null) {
					contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
				}

				InputStreamResource resource = new InputStreamResource(Files.newInputStream(filePath));

				yield ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
						.contentLength(Files.size(filePath))
						.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filePath.getFileName() + "\"")
						.body(resource);
			}

			case S3 -> {
				String presignedUrl = mediaServiceS3.getReadUrl(SecurityUtil.getUserKey(), mediaId);
				yield ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presignedUrl)).build();
			}

			case OSS -> {
				String presignedUrl = mediaServiceOss.getReadUrl(SecurityUtil.getUserKey(), mediaId);
				yield ResponseEntity.status(HttpStatus.FOUND).location(URI.create(presignedUrl)).build();
			}

			default -> throw new IllegalArgumentException("Unexpected value: " + Storage.valueOf(fileDoc.getStorage()));
			};

		} catch (IOException e) {
			return ResponseEntity.status(404).body(null);
		}
	}

	@DeleteMapping("/{mediaId}")
	public ResponseEntity<CommonResponse<?>> delete(@PathVariable String mediaId,
			@RequestParam(required = false) List<String> deleteWithUserKeys) {
		try {
			userFileService.softDeleteAndMarkForCleanupIfOrphaned(mediaId, SecurityUtil.getUserKey(),
					deleteWithUserKeys);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
		} catch (AccessDeniedException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
		}
	}

	@PostMapping("/{mediaId}/share")
	public ResponseEntity<?> share(@PathVariable String mediaId, @RequestParam List<String> shareWithUserKeys) {
		try {
			userFileService.shareFile(mediaId, SecurityUtil.getUserKey(), shareWithUserKeys);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MEDIA_NOT_FOUND));
		} catch (AccessDeniedException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(CommonResponse.from(ResponseCode.MEDIA_ACCESS_DENIED));
		}
	}
}
