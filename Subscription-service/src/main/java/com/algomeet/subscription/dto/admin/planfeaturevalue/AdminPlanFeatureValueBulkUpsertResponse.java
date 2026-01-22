package com.algomeet.subscription.dto.admin.planfeaturevalue;

import java.util.List;
import java.util.UUID;

public record AdminPlanFeatureValueBulkUpsertResponse(
        UUID planId,

        int totalRequested,
        int created,
        int updated,

        List<ItemResult> results
) {
    public record ItemResult(
            UUID featurePropertyId,
            UUID planFeatureValueId,
            Action action
    ) {}

    public enum Action {
        CREATED,
        UPDATED
    }
}