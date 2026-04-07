package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriberResponse {
    private UUID userKey;
    private UUID subscriberKey;
    private Instant createdAt;
}