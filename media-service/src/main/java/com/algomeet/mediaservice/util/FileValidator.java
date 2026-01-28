package com.algomeet.mediaservice.util;

import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.AcceptedFileProperties;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileValidator {
	private final AcceptedFileProperties props;

	public void validate(MultipartFile file, boolean isEncrypted) throws Exception {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}

		String filename = file.getOriginalFilename();
		if (filename == null || filename.isBlank()) {
			throw new FileTypeNotSupportedException("Missing filename");
		}

		String extension = FilenameUtils.getExtension(filename).toLowerCase();

		// Encrypted files: treat as opaque binary
		if (isEncrypted) {
			log.debug("Encrypted file detected, skipping MIME/type validation. filename={}", filename);
			
			if (props.getImageExtensions().contains(extension)
					|| props.getVideoExtensions().contains(extension)
					|| props.getAudioExtensions().contains(extension)
					|| props.getDocumentExtensions().contains(extension)) {
				return;				
			}
			
			throw new FileTypeNotSupportedException("File type not supported: ." + extension);			
		}

		// Plain (non-encrypted) file validation
		Tika tika = new Tika();
		String detectedType = tika.detect(file.getInputStream());
		String declaredType = file.getContentType();

		if (declaredType != null && !declaredType.equals(detectedType)) {
			log.warn("MIME mismatch: declared={} detected={}", declaredType, detectedType);
		}

		if (detectedType.startsWith("image/")) {
			validateExtension(extension, props.getImageExtensions());
		} else if (detectedType.startsWith("video/")) {
			validateExtension(extension, props.getVideoExtensions());
		} else if (detectedType.startsWith("audio/")) {
			validateExtension(extension, props.getAudioExtensions());
		} else if (detectedType.startsWith("application/") || detectedType.startsWith("text/")) {
			validateExtension(extension, props.getDocumentExtensions());
		} else {
			throw new FileTypeNotSupportedException("Unsupported MIME type: " + detectedType);
		}
	}

	private void validateExtension(String extension, Set<String> allowed) {
		if (!allowed.contains(extension)) {
			throw new FileTypeNotSupportedException("File type not supported: ." + extension);
		}
	}
}