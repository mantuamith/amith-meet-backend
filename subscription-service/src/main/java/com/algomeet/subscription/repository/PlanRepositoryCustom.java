package com.algomeet.subscription.repository;

import com.algomeet.subscription.entity.Plan;

public interface PlanRepositoryCustom {
    Plan insert(String code, String name, boolean active);
}
