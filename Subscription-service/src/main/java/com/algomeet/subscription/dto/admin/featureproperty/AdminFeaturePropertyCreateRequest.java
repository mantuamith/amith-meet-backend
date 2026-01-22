package com.algomeet.subscription.dto.admin.featureproperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminFeaturePropertyCreateRequest(
        @NotNull UUID featureId,
        @NotBlank String propKey,
        @NotBlank String label,
        @NotBlank String valueType
) {}
