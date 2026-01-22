package com.algomeet.subscription.dto.admin.planfeaturevalue;

import java.util.UUID;

public record AdminPlanFeatureValueCreateRequest(
        UUID planId,
        UUID featurePropertyId,
        String value
) {}
