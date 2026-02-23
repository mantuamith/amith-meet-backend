package com.algomeet.subscription.api.internal;

import com.algomeet.subscription.service.internal.SubscriptionLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/subscriptions")
@RequiredArgsConstructor
public class InternalSubscriptionController {

    private final SubscriptionLookupService service;

    @GetMapping("/users/{userId}/plan")
    public Map<String, String> getUserPlan(@PathVariable Long userId) {

        String planCode = service.getActivePlanCode(userId);

        return Map.of("planCode", planCode);
    }
}

