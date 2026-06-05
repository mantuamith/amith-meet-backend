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

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;

import java.util.Collections;
import java.util.UUID;

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
    private final StringRedisTemplate redisTemplate; 
	
    private static final int BATCH_SIZE = 500;
    private static final String LOCK_KEY = "lock:scheduler:user-file-cleanup";
    
    // Lua script ensuring atomic "check-then-delete" lock releases to avoid cross-node lease hijacking
    private static final String RELEASE_LUA_SCRIPT = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
	
	/**
     * Runs every 30 minutes. 
     * Uses standard StringRedisTemplate primitive operations for distributed singleton scheduling.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void cleanupExpiredUserFiles() {
        String lockValue = UUID.randomUUID().toString();
        // Lease timeout window set to 15 minutes to guarantee adequate buffer room for batch loops
        long ttlMinutes = 15; 

        boolean acquired = false;
        try {
            // Attempt to acquire distributed lock (SET NX PX equivalent)
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, lockValue, Duration.ofMinutes(ttlMinutes));
            
            acquired = Boolean.TRUE.equals(result);

            if (!acquired) {
                log.debug("Cleanup execution skipped: Another cluster node holds the scheduler lock key.");
                return;
            }

            log.info("Distributed lock acquired successfully [Token: {}]. Starting user-file cleanup task...", lockValue);
            executeCleanupPipeline();

        } catch (Exception e) {
            log.error("Unexpected failure encountered during cleanup scheduling orchestration pipeline", e);
        } finally {
            if (acquired) {
                try {
                    // Execute atomic release validation via Redis engine
                    Long released = redisTemplate.execute(
                            new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class),
                            Collections.singletonList(LOCK_KEY),
                            lockValue
                    );

                    if (Long.valueOf(1L).equals(released)) {
                        log.info("Distributed lock safely released [Token: {}].", lockValue);
                    } else {
                        log.warn("Lock release bypassed: Lock lease expired or was overridden by another process context.");
                    }
                } catch (Exception ex) {
                    log.error("Failed to clean up lock key reference footprint from cache engine", ex);
                }
            }
        }
    }

    private void executeCleanupPipeline() {
        Instant now = Instant.now();
        int totalCleaned = 0;

        while (true) {
            List<UserFileDocument> expiredFiles = 
                    userFileRepository.findCleanupEligible(now, PageRequest.of(0, BATCH_SIZE));

            if (expiredFiles.isEmpty()) {
                break;
            }

            log.info("Processing cleanup batch of {} files", expiredFiles.size());

            for (UserFileDocument file : expiredFiles) {
                try {
                    Storage storageType = Storage.valueOf(file.getStorage().toUpperCase());
                    
                    switch (storageType) {
                        case LOCAL -> mediaServiceLocal.deleteIfExists(file.getAbsolutePath());
                        case S3 -> mediaServiceS3.deleteIfExists(file.getAbsolutePath());
                        case OSS -> mediaServiceOss.deleteIfExists(file.getAbsolutePath());
                        default -> log.warn("Unhandled storage provider type: {}", file.getStorage());
                    }

                    userFileRepository.deleteById(file.getId());
                    totalCleaned++;

                } catch (Exception ex) {
                    log.error("Failed to clean up specific file tracking ID: {}", file.getId(), ex);
                }
            }
        }

        if (totalCleaned > 0) {
            log.info("Completed cleanup execution cycle. Total documents purged: {}", totalCleaned);
        }
    }
}
