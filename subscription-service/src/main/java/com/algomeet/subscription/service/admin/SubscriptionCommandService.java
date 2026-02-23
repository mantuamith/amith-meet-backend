package com.algomeet.subscription.service.admin;

import com.algomeet.subscription.entity.Plan;
import com.algomeet.subscription.entity.UserSubscription;
import com.algomeet.subscription.repository.PlanRepository;
import com.algomeet.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionCommandService {

    private final UserSubscriptionRepository repository;
    private final PlanRepository planRepository;

    public void assignPlan(Long userId, String planCode) {

        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        repository.findByUserIdAndStatus(userId, "ACTIVE")
                .ifPresent(existing -> {
                    existing.setStatus("CANCELLED");
                    existing.setEndAt(Instant.now());
                });

        UserSubscription sub = new UserSubscription();
        sub.setId(UUID.randomUUID());
        sub.setUserId(userId);
        sub.setPlan(plan);
        sub.setStatus("ACTIVE");
        sub.setStartAt(Instant.now());
        sub.setCreatedAt(Instant.now());

        repository.save(sub);
    }
}
