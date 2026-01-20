package com.algomeet.subscription.dto;

import java.util.Map;

public record FeatureItemDto(
    String key,
    String label,
    Map<String, String> values
) {}
