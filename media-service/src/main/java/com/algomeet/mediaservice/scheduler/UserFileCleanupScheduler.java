package com.algomeet.mediaservice.scheduler;

import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.MediaServiceOss;
import com.algomeet.mediaservice.service.MediaServiceS3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserFileCleanupScheduler {
	private final MediaServiceLocal mediaServiceLocal;
	@Autowired(required = false)
	private MediaServiceS3 mediaServiceS3;
	@Autowired(required = false)
	private MediaServiceOss mediaServiceOss;
	private final UserFileRepository userFileRepository;

	/**
	 * Runs every 30 minutes
	 */
	@Scheduled(cron = "0 */59 * * * *")
	public void cleanupExpiredUserFiles() {
		Instant now = Instant.now();

		List<UserFileDocument> expiredFiles =
				userFileRepository.findCleanupEligible(now);

		if (expiredFiles.isEmpty()) {
			return;
		}

		log.info("Found {} user-files eligible for cleanup", expiredFiles.size());

		for (UserFileDocument file : expiredFiles) {
			try {
				// Optional: delete from storage (S3 / local)

				if (Storage.LOCAL.name().equals(file.getStorage())) {
					mediaServiceLocal.deleteIfExists(file.getAbsolutePath());

				} else if(Storage.S3.name().equals(file.getStorage())) {
					mediaServiceS3.deleteIfExists(file.getAbsolutePath());
					
				} else if(Storage.OSS.name().equals(file.getStorage())) {
					mediaServiceOss.deleteIfExists(file.getAbsolutePath());
				}							

				userFileRepository.deleteById(file.getId());

				log.info("Cleaned up file id={}, url={}", file.getId(), file.getAbsolutePath());

			} catch (Exception ex) {
				log.error("Failed to cleanup file id={}", file.getId(), ex);
			}
		}
	}
}
