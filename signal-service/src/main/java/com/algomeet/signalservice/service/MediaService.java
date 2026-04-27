package com.algomeet.signalservice.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.signalservice.client.MediaClient;
import com.algomeet.signalservice.dto.StorageUsageAdjustmentRequest;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

	private final MediaClient mediaClient;
	@Value("${mediaservice.support:false}")
	private boolean mediaServiceSupport;

	public void adjustStorageUsage(String userKey, StorageUsageAdjustmentRequest request) {
		if (!mediaServiceSupport) {return;}
		
		if (!StringUtils.hasText(userKey)) {
			throw new RuntimeException("UserKey has empty value " + userKey);
		}

		try {
			mediaClient.adjustStorageUsage(UUID.fromString(userKey), request);		
		} catch (FeignException.Forbidden ex) {
			log.error("Access denied while updating user storage usage {}", userKey, ex);
			throw new RuntimeException(userKey);
		} catch (FeignException ex) {
			log.error("Media-service error while updating user storage usage {}", userKey, ex);
			throw new RuntimeException("Failed to updating user storage usage", ex);
		} catch (Exception ex) {
			log.error("Unexpected error while updating user storage usage {}", userKey, ex);
			throw new RuntimeException("Unexpected error", ex);
		}
	}
	
	public void deleteStorage(String userKey) {
		if (!mediaServiceSupport) {return;}
		
		if (!StringUtils.hasText(userKey)) {
			throw new RuntimeException("UserKey has empty value " + userKey);
		}

		try {
			mediaClient.deleteUserStorageUsage(UUID.fromString(userKey));		
		} catch (FeignException.Forbidden ex) {
			log.error("Access denied while deleting user storage usage {}", userKey, ex);
			throw new RuntimeException(userKey);
		} catch (FeignException ex) {
			log.error("Media-service error while deleting user storage usage {}", userKey, ex);
			throw new RuntimeException("Failed to delete user storage usage ", ex);
		} catch (Exception ex) {
			log.error("Unexpected error while deleting user storage usage {}", userKey, ex);
			throw new RuntimeException("Unexpected error", ex);
		}
	}
}
