package com.algomeet.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(
    name = "subscription-service",
    url = "${feign.client.subscription-service.url}"
)
public interface SubscriptionClient {

    @GetMapping("/internal/subscriptions/users/{userId}/plan")
    Map<String, String> getUserPlan(@PathVariable("userId") Long userId);
}
