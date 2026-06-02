package com.algomeet.userservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Thin HTTP client for calling media-service internal APIs on behalf of user-service.
 * All calls are best-effort: failures are logged but never propagate to the caller,
 * so a media-service outage cannot block account deletion.
 */
@Slf4j
@Component
public class MediaServiceClient {

    private final RestTemplate restTemplate;
    private final String mediaServiceUrl;
    private final boolean enabled;

    public MediaServiceClient(
            RestTemplate restTemplate,
            @Value("${media.service.url:http://localhost:8095}") String mediaServiceUrl,
            @Value("${media.service.enabled:true}") boolean enabled) {
        this.restTemplate = restTemplate;
        this.mediaServiceUrl = mediaServiceUrl;
        this.enabled = enabled;
    }

    /**
     * Deletes the storage-usage record for the given user from media-service (PostgreSQL).
     * Called during account deletion to prevent orphaned quota rows.
     *
     * @param userKey the UUID of the user being deleted
     */
    public void deleteStorageUsage(UUID userKey) {
        if (!enabled) {
            log.debug("media-service integration disabled — skipping storage-usage cleanup for userKey={}", userKey);
            return;
        }

        String url = mediaServiceUrl + "/internal/media/users/" + userKey + "/storage-usage";
        try {
            restTemplate.delete(url);
            log.info("Deleted media storage-usage record for userKey={}", userKey);
        } catch (HttpClientErrorException.NotFound ex) {
            // No storage record existed — that's fine, nothing to clean up
            log.debug("No media storage-usage record found for userKey={} — nothing to delete", userKey);
        } catch (Exception ex) {
            // Best-effort: log and continue. Do NOT fail the account deletion.
            log.warn("Could not delete media storage-usage for userKey={}: {}", userKey, ex.getMessage());
        }
    }
}
