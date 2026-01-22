package com.algomeet.subscription.service.admin.planfeaturevalue;

import com.algomeet.subscription.dto.admin.planfeaturevalue.*;
import com.algomeet.subscription.entity.*;
import com.algomeet.subscription.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminPlanFeatureValueService {

    private final PlanRepository planRepository;
    private final FeaturePropertyRepository featurePropertyRepository;
    private final PlanFeatureValueRepository planFeatureValueRepository;

    /* ---------------- CREATE ---------------- */

    public AdminPlanFeatureValueResponse create(
            AdminPlanFeatureValueCreateRequest request
    ) {

        if (planFeatureValueRepository
                .findByPlanIdAndFeaturePropertyId(
                        request.planId(),
                        request.featurePropertyId()
                ).isPresent()) {

            throw new IllegalStateException(
                    "Value already exists for plan + property"
            );
        }

        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        FeatureProperty property =
                featurePropertyRepository.findById(request.featurePropertyId())
                        .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        validateValue(property.getValueType(), request.value());

        PlanFeatureValue pfv = new PlanFeatureValue();
        pfv.setId(UUID.randomUUID());
        pfv.setPlan(plan);
        pfv.setFeatureProperty(property);
        pfv.setValue(request.value());

        return toDto(planFeatureValueRepository.save(pfv));
    }

    /* ---------------- UPDATE ---------------- */

    public AdminPlanFeatureValueResponse update(
            UUID id,
            AdminPlanFeatureValueUpdateRequest request
    ) {

        PlanFeatureValue pfv = planFeatureValueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Value not found"));

        validateValue(
                pfv.getFeatureProperty().getValueType(),
                request.value()
        );

        pfv.setValue(request.value());
        return toDto(pfv);
    }

    /* ---------------- LIST BY PLAN ---------------- */

    @Transactional(readOnly = true)
    public List<AdminPlanFeatureValueResponse> listByPlan(UUID planId) {
        return planFeatureValueRepository.findAllByPlanId(planId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    /* ---------------- DELETE ---------------- */

    public void delete(UUID id) {
        planFeatureValueRepository.deleteById(id);
    }

    /* ---------------- HELPERS ---------------- */

    private void validateValue(String valueType, String value) {

        switch (valueType) {
            case "BOOLEAN" -> {
                if (!value.equalsIgnoreCase("true")
                        && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Expected BOOLEAN value");
                }
            }
            case "NUMBER" -> {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected NUMBER value");
                }
            }
            case "STRING" -> {
                if (value.isBlank()) {
                    throw new IllegalArgumentException("Value cannot be empty");
                }
            }
        }
    }

    public AdminPlanFeatureValueBulkUpsertResponse bulkUpsert(
            UUID planId,
            AdminPlanFeatureValueBulkUpsertRequest request
    ) {

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        int created = 0;
        int updated = 0;

        List<AdminPlanFeatureValueBulkUpsertResponse.ItemResult> results =
                new ArrayList<>();

        for (var item : request.values()) {

            FeatureProperty property =
                    featurePropertyRepository.findById(item.featurePropertyId())
                            .orElseThrow(() -> new IllegalArgumentException("Property not found"));

            validateValue(property.getValueType(), item.value());

            Optional<PlanFeatureValue> existing =
                    planFeatureValueRepository.findByPlanIdAndFeaturePropertyId(
                            planId,
                            item.featurePropertyId()
                    );

            PlanFeatureValue pfv;
            AdminPlanFeatureValueBulkUpsertResponse.Action action;

            if (existing.isPresent()) {
                pfv = existing.get();
                action = AdminPlanFeatureValueBulkUpsertResponse.Action.UPDATED;
                updated++;
            } else {
                pfv = new PlanFeatureValue();
                pfv.setId(UUID.randomUUID());
                pfv.setPlan(plan);
                pfv.setFeatureProperty(property);
                action = AdminPlanFeatureValueBulkUpsertResponse.Action.CREATED;
                created++;
            }

            pfv.setValue(item.value());
            planFeatureValueRepository.save(pfv);

            results.add(
                    new AdminPlanFeatureValueBulkUpsertResponse.ItemResult(
                            item.featurePropertyId(),
                            pfv.getId(),
                            action
                    )
            );
        }

        return new AdminPlanFeatureValueBulkUpsertResponse(
                planId,
                request.values().size(),
                created,
                updated,
                results
        );
    }

    private AdminPlanFeatureValueResponse toDto(PlanFeatureValue pfv) {

        FeatureProperty fp = pfv.getFeatureProperty();
        Feature f = fp.getFeature();

        return new AdminPlanFeatureValueResponse(
                pfv.getId(),
                pfv.getPlan().getId(),
                pfv.getPlan().getCode(),
                f.getCode(),
                fp.getPropKey(),
                fp.getLabel(),
                pfv.getValue()
        );
    }
}
