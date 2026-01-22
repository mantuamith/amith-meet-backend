package com.algomeet.subscription.service.admin.entitlement;

import com.algomeet.subscription.dto.entitlement.EntitlementCheckRequest;
import com.algomeet.subscription.dto.entitlement.EntitlementCheckResponse;
import com.algomeet.subscription.entity.Plan;
import com.algomeet.subscription.entity.PlanFeatureValue;
import com.algomeet.subscription.repository.PlanFeatureValueRepository;
import com.algomeet.subscription.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntitlementService {

    private final PlanRepository planRepository;
    private final PlanFeatureValueRepository planFeatureValueRepository;

    public EntitlementCheckResponse check(EntitlementCheckRequest request) {

        Plan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        List<PlanFeatureValue> values =
                planFeatureValueRepository.findAllByPlanCode(plan.getCode());

        Map<String, PlanFeatureValue> lookup = values.stream()
                .collect(Collectors.toMap(
                        pfv -> key(
                                pfv.getFeatureProperty().getFeature().getCode(),
                                pfv.getFeatureProperty().getPropKey()
                        ),
                        pfv -> pfv
                ));

        List<EntitlementCheckResponse.Item> results =
                request.checks().stream()
                        .map(item -> resolve(item, lookup))
                        .toList();

        return new EntitlementCheckResponse(plan.getCode(), results);
    }

    /* ---------------- HELPERS ---------------- */

    private EntitlementCheckResponse.Item resolve(
            EntitlementCheckRequest.Item item,
            Map<String, PlanFeatureValue> lookup
    ) {

        PlanFeatureValue pfv = lookup.get(
                key(item.feature(), item.property())
        );

        if (pfv == null) {
            return new EntitlementCheckResponse.Item(
                    item.feature(),
                    item.property(),
                    null,
                    false
            );
        }

        boolean allowed = computeAllowed(
                pfv.getFeatureProperty().getValueType(),
                pfv.getValue()
        );

        return new EntitlementCheckResponse.Item(
                item.feature(),
                item.property(),
                pfv.getValue(),
                allowed
        );
    }

    private boolean computeAllowed(String type, String value) {
        return switch (type) {
            case "BOOLEAN" -> Boolean.parseBoolean(value);
            case "NUMBER"  -> Integer.parseInt(value) > 0;
            case "STRING"  -> !value.isBlank();
            default        -> false;
        };
    }

    private String key(String feature, String property) {
        return feature + "::" + property;
    }
}
