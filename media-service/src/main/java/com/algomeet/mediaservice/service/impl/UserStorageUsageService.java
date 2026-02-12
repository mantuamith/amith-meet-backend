package com.algomeet.mediaservice.service.impl;

import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.dto.StorageUsageResponse;
import com.algomeet.mediaservice.entity.UserStorageUsage;
import com.algomeet.mediaservice.repository.UserStorageUsageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStorageUsageService {
    private final UserStorageUsageRepository repository;
    
    @Transactional
    public StorageUsageResponse getUsage(UUID userId) {
    	 UserStorageUsage usage = repository.findByUserKeyForUpdate(userId)
                 .orElseGet(() -> createNew(userId));
         return mapToResponse(usage);
    }

    @Transactional
    public StorageUsageResponse adjustUsage(UUID userKey, StorageUsageAdjustmentRequest request) {

        UserStorageUsage usage = repository.findByUserKeyForUpdate(userKey)
                .orElseGet(() -> createNew(userKey));

        applyDelta(usage, request);

        usage.setTotalStorageUsed(
                usage.getMediaStorageUsed() + usage.getChatStorageUsed()
        );

        repository.save(usage);

        log.info("Storage updated for userKey={} ref={}", userKey, request.getReferenceId());

        return mapToResponse(usage);
    }

    private UserStorageUsage createNew(UUID userKey) {
        return UserStorageUsage.builder()
                .userKey(userKey)
                .mediaStorageUsed(0)
                .mediaFileCount(0)
                .chatStorageUsed(0)
                .chatMessageCount(0)
                .totalStorageUsed(0)
                .build();
    }

    private void applyDelta(UserStorageUsage u, StorageUsageAdjustmentRequest r) {

        if (r.getMediaStorageBytesDelta() != null)
            u.setMediaStorageUsed(Math.max(0, u.getMediaStorageUsed() + r.getMediaStorageBytesDelta()));

        if (r.getMediaFileCountDelta() != null)
            u.setMediaFileCount(Math.max(0, u.getMediaFileCount() + r.getMediaFileCountDelta()));

        if (r.getChatStorageBytesDelta() != null)
            u.setChatStorageUsed(Math.max(0, u.getChatStorageUsed() + r.getChatStorageBytesDelta()));

        if (r.getChatMessageCountDelta() != null)
            u.setChatMessageCount(Math.max(0, u.getChatMessageCount() + r.getChatMessageCountDelta()));
    }

    private StorageUsageResponse mapToResponse(UserStorageUsage u) {
        return StorageUsageResponse.builder()
                .userKey(u.getUserKey())
                .mediaStorageUsed(u.getMediaStorageUsed())
                .mediaFileCount(u.getMediaFileCount())
                .chatStorageUsed(u.getChatStorageUsed())
                .chatMessageCount(u.getChatMessageCount())
                .totalStorageUsed(u.getTotalStorageUsed())
                .lastUpdated(u.getLastUpdated())
                .build();
    }
}
