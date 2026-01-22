package com.algomeet.subscription.dto.admin.plan;

import java.util.UUID;

public record AdminPlanResponse(
        UUID id,
        String code,
        String name,
        boolean active
) {}
