package com.algomeet.signalservice.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class SubscriberRequest {
    private UUID userKey;
    private UUID subscriberKey;
}