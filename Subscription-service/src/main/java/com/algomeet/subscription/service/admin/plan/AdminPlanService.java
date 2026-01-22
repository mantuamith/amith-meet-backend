package com.algomeet.subscription.service.admin.plan;

import com.algomeet.subscription.dto.admin.plan.AdminPlanCreateRequest;
import com.algomeet.subscription.dto.admin.plan.AdminPlanResponse;
import com.algomeet.subscription.dto.admin.plan.AdminPlanUpdateRequest;
import com.algomeet.subscription.entity.Plan;
import com.algomeet.subscription.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPlanService {

    private final PlanRepository planRepository;

    public AdminPlanResponse create(AdminPlanCreateRequest request) {

        Plan plan = planRepository.insert(
                request.code(),
                request.name(),
                true
        );

        return toDto(plan);
    }

    public List<AdminPlanResponse> findAll() {
        return planRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public void updateStatus(UUID id, boolean active) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        plan.setActive(active);
        planRepository.save(plan);
    }

    public AdminPlanResponse update(UUID id, AdminPlanUpdateRequest request) {

        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found"));

        plan.setName(request.name());
        plan.setActive(request.active());

        Plan saved = planRepository.save(plan);

        return toDto(saved);
    }


    private AdminPlanResponse toDto(Plan plan) {
        return new AdminPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.isActive()
        );
    }
}
