package com.algomeet.subscription.dto;

import java.util.List;

public record PlanComparisonResponse(
    List<PlanDto> plans,
    List<FeatureGroupDto> features
) {}
