package com.algomeet.mediaservice.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.MediaUploadResponse;

public interface MediaService {

	MediaUploadResponse upload(
			String userKey,
			MultipartFile file,
			String contentType,
			boolean encrypted,
			boolean autoExpire
			);
	
    boolean deleteIfExists(String fileLocation) throws IOException;
}
