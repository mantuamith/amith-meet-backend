package com.algomeet.subscription.dto.admin.planfeaturevalue;

import java.util.UUID;

public record AdminPlanFeatureValueResponse(
        UUID id,
        UUID planId,
        String planCode,
        String featureCode,
        String propertyKey,
        String label,
        String value
) {}
