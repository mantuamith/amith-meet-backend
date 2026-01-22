package com.algomeet.subscription.dto.admin.feature;

import java.util.UUID;

public record AdminFeatureResponse(
        UUID id,
        String code,
        String name,
        String uiGroup,
        Integer displayOrder
) {}
