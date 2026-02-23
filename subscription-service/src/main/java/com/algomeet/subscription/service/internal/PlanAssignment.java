package com.algomeet.subscription.service.internal;

import com.algomeet.subscription.service.admin.SubscriptionCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/subscriptions")
@RequiredArgsConstructor
public class PlanAssignment {

    private SubscriptionCommandService commandService;

    @PostMapping("/users/{userId}/plan/{planCode}")
    public void assignPlan(@PathVariable Long userId,
                           @PathVariable String planCode) {

        commandService.assignPlan(userId, planCode);
    }
}