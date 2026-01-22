package com.algomeet.subscription.dto.admin.featureproperty;

import jakarta.validation.constraints.NotBlank;

public record AdminFeaturePropertyUpdateRequest(
        @NotBlank String label,
        @NotBlank String valueType
) {}
