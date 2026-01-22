package com.algomeet.subscription.dto.admin.feature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminFeatureCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String uiGroup,
        @NotNull Integer displayOrder
) {}
