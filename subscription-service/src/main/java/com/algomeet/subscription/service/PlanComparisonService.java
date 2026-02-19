package com.algomeet.subscription.service;

import com.algomeet.subscription.dto.FeatureGroupDto;
import com.algomeet.subscription.dto.FeatureItemDto;
import com.algomeet.subscription.dto.PlanComparisonResponse;
import com.algomeet.subscription.dto.PlanDto;
import com.algomeet.subscription.entity.FeatureProperty;
import com.algomeet.subscription.entity.Plan;
import com.algomeet.subscription.entity.PlanFeatureValue;
import com.algomeet.subscription.repository.FeaturePropertyRepository;
import com.algomeet.subscription.repository.PlanFeatureValueRepository;
import com.algomeet.subscription.repository.PlanRepository;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanComparisonService {

    private final PlanRepository planRepository;
    private final FeaturePropertyRepository featurePropertyRepository;
    private final PlanFeatureValueRepository planFeatureValueRepository;

    @Cacheable(cacheNames = "plan-comparison")
    public PlanComparisonResponse getComparison() {

        List<Plan> plans = planRepository.findByIsActiveTrueOrderByIdAsc();
        List<FeatureProperty> properties = featurePropertyRepository.findAllWithFeature();
        List<PlanFeatureValue> values = planFeatureValueRepository.findAllForComparison();

        return buildResponse(plans, properties, values);
    }

    private PlanComparisonResponse buildResponse(
            List<Plan> plans,
            List<FeatureProperty> properties,
            List<PlanFeatureValue> values
    ) {

        // Build planId → planCode safely (NO Collectors.toMap)
        Map<UUID, String> planIdToCode = new HashMap<>();

        for (Plan plan : plans) {

            if (plan.getId() == null) {
                log.error("Skipping plan with NULL id: {}", plan);
                continue;
            }

            if (plan.getCode() == null || plan.getCode().isBlank()) {
                log.error("Skipping plan with invalid code. planId={}", plan.getId());
                continue;
            }

            planIdToCode.put(plan.getId(), plan.getCode());
        }

        // PropertyId → (PlanCode → Value)
        Map<UUID, Map<String, String>> valueMatrix = new HashMap<>();

        for (PlanFeatureValue pfv : values) {

            if (pfv.getPlan() == null || pfv.getFeatureProperty() == null) {
                log.error("Skipping PFV {} due to NULL relations", pfv.getId());
                continue;
            }

            UUID planId = pfv.getPlan().getId();
            UUID propId = pfv.getFeatureProperty().getId();

            if (propId == null) {
                log.error("Skipping PFV {} because propertyId is NULL", pfv.getId());
                continue;
            }

            String planCode = planIdToCode.get(planId);

            if (planCode == null) {
                log.error("Skipping PFV {} because resolved planCode is NULL (planId={})",
                        pfv.getId(), planId);
                continue;
            }

            Map<String, String> planValues =
                    valueMatrix.computeIfAbsent(propId, k -> new HashMap<>());

            // Never insert NULL key into map
            planValues.put(planCode, pfv.getValue());
        }

        return buildDto(plans, properties, valueMatrix);
    }

    private PlanComparisonResponse buildDto(
            List<Plan> plans,
            List<FeatureProperty> properties,
            Map<UUID, Map<String, String>> valueMatrix
    ) {

        List<PlanDto> planDtos = new ArrayList<>();

        for (Plan p : plans) {
            if (p.getCode() == null) {
                log.warn("Skipping plan {} due to NULL code during DTO build", p.getId());
                continue;
            }
            planDtos.add(new PlanDto(p.getCode(), p.getName()));
        }

        Map<String, List<FeatureItemDto>> grouped = new LinkedHashMap<>();

        for (FeatureProperty prop : properties) {

            if (prop.getFeature() == null) {
                log.error("Property {} has NULL feature reference", prop.getId());
                continue;
            }

            String group = prop.getFeature().getUiGroup();

            // Prevent NULL map key
            if (group == null || group.isBlank()) {
                log.warn("Feature {} has invalid ui_group → assigning OTHER",
                        prop.getFeature().getId());
                group = "OTHER";
            }

            String propKey = prop.getPropKey();

            if (propKey == null || propKey.isBlank()) {
                log.error("Skipping property {} due to invalid prop_key", prop.getId());
                continue;
            }

            Map<String, String> values =
                    valueMatrix.getOrDefault(prop.getId(), Collections.emptyMap());

            FeatureItemDto item = new FeatureItemDto(
                    propKey,
                    prop.getLabel(),
                    values
            );

            grouped.computeIfAbsent(group, g -> new ArrayList<>()).add(item);
        }

        List<FeatureGroupDto> featureGroups = grouped.entrySet().stream()
                .map(e -> new FeatureGroupDto(e.getKey(), e.getValue()))
                .toList();

        return new PlanComparisonResponse(planDtos, featureGroups);
    }
}
