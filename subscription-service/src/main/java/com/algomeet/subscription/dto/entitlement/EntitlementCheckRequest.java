package com.algomeet.subscription.dto.entitlement;

import java.util.List;

public record EntitlementCheckRequest(
        String planCode,
        List<Item> checks
) {
    public record Item(
            String feature,
            String property
    ) {}
}
