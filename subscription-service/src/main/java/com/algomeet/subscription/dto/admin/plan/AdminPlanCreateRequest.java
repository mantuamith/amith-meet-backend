package com.algomeet.subscription.dto.admin.plan;

import jakarta.validation.constraints.NotBlank;

public record AdminPlanCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        boolean active
) {}
