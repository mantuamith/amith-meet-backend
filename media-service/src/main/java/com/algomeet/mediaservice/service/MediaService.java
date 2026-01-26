package com.algomeet.mediaservice.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.MediaUploadResponse;

public interface MediaService {

	MediaUploadResponse upload(
			String userKey,
			List<String> sharedWithUserKeys,
			MultipartFile file,
			String contentType,
			boolean encrypted
			);
	
    boolean deleteIfExists(String fileLocation) throws IOException;
}
