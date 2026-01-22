package com.algomeet.subscription.dto.admin.planfeaturevalue;

import java.util.List;
import java.util.UUID;

public record AdminPlanFeatureValueBulkUpsertRequest(
        List<Item> values
) {
    public record Item(
            UUID featurePropertyId,
            String value
    ) {}
}
