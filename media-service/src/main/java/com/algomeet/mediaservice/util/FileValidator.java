package com.algomeet.mediaservice.util;

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

    public void validate(MultipartFile file) throws Exception  { 
    	Tika tika = new Tika();
    	String detectedType = tika.detect(file.getInputStream());
    	
    	String filename = file.getOriginalFilename();
    	String extension = FilenameUtils.getExtension(filename).toLowerCase();
    	
    	String declaredType = file.getContentType();

    	if (!detectedType.equals(declaredType)) {
    	    log.warn("MIME mismatch: declared={} detected={}", declaredType, detectedType);
    	}
    	
    	if (detectedType.startsWith("image")) {
    		if (!props.getImageExtensions().contains(extension)) {
    			throw new FileTypeNotSupportedException("File type not supported " + extension);
    		}
    	} else if (detectedType.startsWith("video")) {
    		if (!props.getVideoExtensions().contains(extension)) {
    			throw new FileTypeNotSupportedException("File type not supported " + extension);
    		}
    	} else if (detectedType.startsWith("audio")) {
    		if (!props.getAudioExtensions().contains(extension)) {
    			throw new FileTypeNotSupportedException("File type not supported " + extension);
    		}
    	} else if (detectedType.startsWith("application") || detectedType.startsWith("text")) {
    		if (!props.getDocumentExtensions().contains(extension)) {
    			throw new FileTypeNotSupportedException("File type not supported " + extension);
    		}
    	} else{
    		throw new FileTypeNotSupportedException("File type not supported " + extension);
    	}
    }
}