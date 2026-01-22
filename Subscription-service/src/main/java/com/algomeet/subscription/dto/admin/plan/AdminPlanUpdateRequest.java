package com.algomeet.subscription.dto.admin.plan;

import jakarta.validation.constraints.NotBlank;

public record AdminPlanUpdateRequest(
        @NotBlank String name,
        boolean active
) {}
