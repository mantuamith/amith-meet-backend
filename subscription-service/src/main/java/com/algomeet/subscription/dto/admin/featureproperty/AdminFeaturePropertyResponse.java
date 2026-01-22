package com.algomeet.subscription.dto.admin.featureproperty;

import java.util.UUID;

public record AdminFeaturePropertyResponse(
        UUID id,
        UUID featureId,
        String featureCode,
        String propKey,
        String label,
        String valueType
) {}
