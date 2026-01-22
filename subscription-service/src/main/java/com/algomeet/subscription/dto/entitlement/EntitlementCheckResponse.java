package com.algomeet.subscription.dto.entitlement;

import java.util.List;

public record EntitlementCheckResponse(
        String planCode,
        List<Item> results
) {
    public record Item(
            String feature,
            String property,
            String value,
            boolean allowed
    ) {}
}
