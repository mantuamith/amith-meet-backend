package com.algomeet.subscription.service.internal;

import com.algomeet.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionLookupService {

    private final UserSubscriptionRepository repository;

    public String getActivePlanCode(Long userId) {

        return repository.findByUserIdAndStatus(userId, "ACTIVE")
                .map(sub -> sub.getPlan().getCode())
                .orElse("FREE"); // default fallback
    }
}
