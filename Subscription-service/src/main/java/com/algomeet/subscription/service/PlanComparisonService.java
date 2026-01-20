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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanComparisonService {

    private final PlanRepository planRepository;
    private final FeaturePropertyRepository featurePropertyRepository;
    private final PlanFeatureValueRepository planFeatureValueRepository;

    public PlanComparisonResponse getComparison() {

        List<Plan> plans = planRepository.findByIsActiveTrueOrderByIdAsc();
        List<FeatureProperty> properties =
                featurePropertyRepository.findAllWithFeature();
        List<PlanFeatureValue> values =
                planFeatureValueRepository.findAllForComparison();

        return buildResponse(plans, properties, values);
    }
    private PlanComparisonResponse buildResponse(
            List<Plan> plans,
            List<FeatureProperty> properties,
            List<PlanFeatureValue> values
    ) {
        // PlanCode → PlanId
        Map<UUID, String> planIdToCode = plans.stream()
                .collect(Collectors.toMap(Plan::getId, Plan::getCode));

        // PropertyId → (PlanCode → Value)
        Map<UUID, Map<String, String>> valueMatrix = new HashMap<>();

        for (PlanFeatureValue pfv : values) {
            UUID propId = pfv.getFeatureProperty().getId();
            String planCode = planIdToCode.get(pfv.getPlan().getId());

            valueMatrix
                    .computeIfAbsent(propId, k -> new HashMap<>())
                    .put(planCode, pfv.getValue());
        }

        return buildDto(plans, properties, valueMatrix);
    }

    private PlanComparisonResponse buildDto(
            List<Plan> plans,
            List<FeatureProperty> properties,
            Map<UUID, Map<String, String>> valueMatrix
    ) {
        List<PlanDto> planDtos = plans.stream()
                .map(p -> new PlanDto(p.getCode(), p.getName()))
                .toList();

        Map<String, List<FeatureItemDto>> grouped = new LinkedHashMap<>();

        for (FeatureProperty prop : properties) {
            String group = prop.getFeature().getUiGroup();

            Map<String, String> values =
                    valueMatrix.getOrDefault(prop.getId(), Map.of());

            FeatureItemDto item = new FeatureItemDto(
                    prop.getPropKey(),
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
