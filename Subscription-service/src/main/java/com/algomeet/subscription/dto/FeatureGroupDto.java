package com.algomeet.subscription.dto;

import java.util.List;

public record FeatureGroupDto(
    String group,
    List<FeatureItemDto> items
) {}
